<script setup>
// 管理员后台：5 个标签页
// 1) 概览   — 系统统计卡片
// 2) 用户管理 — 用户列表（禁用/启用）
// 3) 内容管理 — Prompt 列表 + 关键词搜索 + 数据导出
// 4) 分类管理 — 分类的新增/删除（分类下还有 Prompt 时不允许删除）
// 5) 权限管理 — 角色 × 权限点（RBAC 权限维护子系统）
// 后端所有管理类接口均带 @SaCheckRole("ADMIN") + @SaCheckPermission("xxx")
import { ref, onMounted, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getStats, getAdminUserList, updateUserStatus, getAdminPromptList, exportPrompts } from '@/api/admin'
import { deletePrompt } from '@/api/prompt'
import { getCategoryList, addCategory, deleteCategory } from '@/api/category'
import { getRoleList, getPermissionList, assignRolePermissions } from '@/api/rbac'
import { formatTime } from '@/utils/format'

const router = useRouter()
const activeTab = ref('overview')

// ---------- 概览：统计卡片 ----------
const stats = ref(null)

// ---------- 用户管理 ----------
const userList = ref([])
const userTotal = ref(0)
const userLoading = ref(false)
const userPage = ref({ pageNum: 1, pageSize: 10 })

async function loadUsers() {
  userLoading.value = true
  try {
    const page = await getAdminUserList(userPage.value)
    userList.value = page.records
    userTotal.value = page.total
  } finally {
    userLoading.value = false
  }
}

// 角色标签配色：SUPER_ADMIN 权限最高用 danger，ADMIN 用 warning，普通用户 info
function roleTagType(role) {
  if (role === 'SUPER_ADMIN') return 'danger'
  if (role === 'ADMIN') return 'warning'
  return 'info'
}

// ADMIN / SUPER_ADMIN 不接受启停操作：
// 后端 UserServiceImpl 会再次拒绝（ADMIN 防互相禁用锁死，SUPER_ADMIN 是权限兜底的最终治理者），
// 这里只是提前隐藏按钮，不把无效操作暴露给用户。
function canToggleStatus(row) {
  return row.role !== 'ADMIN' && row.role !== 'SUPER_ADMIN'
}

async function toggleUserStatus(user) {
  const disable = user.status === 1
  try {
    await ElMessageBox.confirm(
      disable ? `禁用后 ${user.username} 会被立即踢下线，确定禁用？` : `确定恢复 ${user.username} 的使用权限？`,
      disable ? '禁用确认' : '启用确认',
      { type: 'warning' },
    )
  } catch { return }
  await updateUserStatus(user.id, disable ? 0 : 1)
  user.status = disable ? 0 : 1
  ElMessage.success(disable ? '已禁用并踢下线' : '已启用')
}

// ---------- 内容管理 ----------
const promptList = ref([])
const promptTotal = ref(0)
const promptLoading = ref(false)
const promptQuery = ref({ pageNum: 1, pageSize: 10, keyword: '' })

async function loadPrompts() {
  promptLoading.value = true
  try {
    const page = await getAdminPromptList({
      pageNum: promptQuery.value.pageNum,
      pageSize: promptQuery.value.pageSize,
      keyword: promptQuery.value.keyword || undefined,
    })
    promptList.value = page.records
    promptTotal.value = page.total
  } finally {
    promptLoading.value = false
  }
}

function handlePromptSearch() {
  promptQuery.value.pageNum = 1
  loadPrompts()
}

// 输入防抖：停止输入 300ms 后表格自动过滤成搜索结果，让"看到的内容 = 将导出的内容"
let promptSearchTimer = null
watch(() => promptQuery.value.keyword, () => {
  clearTimeout(promptSearchTimer)
  promptSearchTimer = setTimeout(() => {
    promptQuery.value.pageNum = 1
    loadPrompts()
  }, 300)
})

async function handleDeletePrompt(item) {
  try {
    await ElMessageBox.confirm(`确定删除「${item.title}」？相关收藏和使用记录会一并清除`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger',
    })
  } catch { return }
  await deletePrompt(item.id)
  ElMessage.success('删除成功')
  loadPrompts()
  stats.value = await getStats()
}

async function handleExport() {
  // 导出前确认范围：表格实时过滤后，total 就是关键词命中的总数，让用户明确知道导出什么
  const kw = (promptQuery.value.keyword || '').trim()
  const confirmMsg = kw
    ? `将导出关键词「${kw}」匹配的全部 <b>${promptTotal.value}</b> 条数据（即当前表格筛选结果）`
    : `当前未输入关键词，将导出<b>全部 ${promptTotal.value}</b> 条数据`
  try {
    await ElMessageBox.confirm(confirmMsg, '导出确认', {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '导出',
      cancelButtonText: '取消',
    })
  } catch { return }

  const blob = await exportPrompts(promptQuery.value.keyword || undefined)

  // 空结果提前拦截：文件里只有表头时直接提示，避免下载一个"空 Excel"让用户误以为功能坏了
  const text = await blob.text()
  const body = text.replace(/^\uFEFF/, '').trim()
  if (!body || body === 'ID,标题,内容,描述,分类,作者,浏览数,收藏数,发布时间') {
    ElMessage.warning('当前关键词没有匹配到任何数据，试试标题、描述或分类名（如"编程"）')
    return
  }

  const url = URL.createObjectURL(new Blob([blob], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  const pad = (n) => String(n).padStart(2, '0')
  const t = new Date()
  a.download = `Prompt数据_${t.getFullYear()}${pad(t.getMonth() + 1)}${pad(t.getDate())}${pad(t.getHours())}${pad(t.getMinutes())}${pad(t.getSeconds())}.csv`
  document.body.appendChild(a); a.click(); a.remove()
  // 不能立即 revokeObjectURL：浏览器下载管理器是异步取数据的，
  // 过早释放 blob URL 会拿到 0 字节文件（下载"成功"但内容为空）
  setTimeout(() => URL.revokeObjectURL(url), 10000)
  ElMessage.success('导出成功')
}

// ---------- 分类管理 ----------
const categoryList = ref([])
const categoryLoading = ref(false)
const categorySaving = ref(false)
const categoryForm = reactive({ name: '', description: '' })

async function loadCategories() {
  categoryLoading.value = true
  try {
    // 拦截器已拆 Result 信封，这里直接拿到 Category[]
    categoryList.value = await getCategoryList()
  } finally {
    categoryLoading.value = false
  }
}

// 新增分类：前端先做与 CategoryDTO 一致的长度校验，后端 @Validated 兜底（重名会返回"分类名称已存在"）
async function handleAddCategory() {
  const name = categoryForm.name.trim()
  if (!name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  if (name.length > 50) {
    ElMessage.warning('分类名称长度不能超过 50')
    return
  }
  categorySaving.value = true
  try {
    await addCategory({
      name,
      // 空描述不传该字段，让后端存 null 而不是空串
      description: categoryForm.description.trim() || undefined,
    })
    ElMessage.success('分类已新增')
    categoryForm.name = ''
    categoryForm.description = ''
    loadCategories()
  } finally {
    categorySaving.value = false
  }
}

// 删除分类：后端会检查该分类下是否还有 Prompt，有则返回业务错误（拦截器统一提示）
async function handleDeleteCategory(item) {
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${item.name}」？若该分类下还有 Prompt，后端会拒绝删除`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' },
    )
  } catch { return }
  await deleteCategory(item.id)
  ElMessage.success('删除成功')
  loadCategories()
}

// ---------- 权限管理（RBAC） ----------
const roleList = ref([])
const permList = ref([])
const rbacLoading = ref(false)
const rbacDialog = reactive({ visible: false, role: null, draft: [], saving: false })

async function loadRbac() {
  rbacLoading.value = true
  try {
    const [roles, perms] = await Promise.all([getRoleList(), getPermissionList()])
    roleList.value = roles
    permList.value = perms
  } finally { rbacLoading.value = false }
}

function openAssignDialog(role) {
  if (role.code === 'SUPER_ADMIN') {
    ElMessage.warning('SUPER_ADMIN 是系统内置角色，权限不可修改')
    return
  }
  rbacDialog.role = role
  rbacDialog.draft = [...role.permissionCodes]
  rbacDialog.visible = true
}

async function saveAssign() {
  rbacDialog.saving = true
  try {
    await assignRolePermissions(rbacDialog.role.code, rbacDialog.draft)
    ElMessage.success(`已更新 ${rbacDialog.role.name} 的权限`)
    rbacDialog.visible = false
    loadRbac()
  } finally { rbacDialog.saving = false }
}

// 把权限点按模块分组（用于权限分配弹窗）
function permByModule() {
  const m = {}
  for (const p of permList.value) {
    if (!m[p.module]) m[p.module] = []
    m[p.module].push(p)
  }
  return m
}

const moduleNames = { '提示词': '提示词管理', '分类': '分类管理', '用户': '用户管理', '数据': '数据管理', '权限': '权限管理' }

onMounted(async () => {
  loadUsers()
  loadPrompts()
  loadCategories()
  loadRbac()
  stats.value = await getStats()
})
</script>

<template>
  <div>
    <el-tabs v-model="activeTab" class="admin-tabs">
      <!-- 1. 概览 -->
      <el-tab-pane label="概览" name="overview">
        <div class="stats-row">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-num">{{ stats?.userCount ?? '-' }}</div>
            <div class="stat-label"><el-icon><UserFilled /></el-icon> 用户总数</div>
          </el-card>
          <el-card class="stat-card" shadow="hover">
            <div class="stat-num">{{ stats?.promptCount ?? '-' }}</div>
            <div class="stat-label"><el-icon><Document /></el-icon> Prompt 总数</div>
          </el-card>
          <el-card class="stat-card" shadow="hover">
            <div class="stat-num">{{ stats?.favoriteCount ?? '-' }}</div>
            <div class="stat-label"><el-icon><Star /></el-icon> 收藏总数</div>
          </el-card>
          <el-card class="stat-card" shadow="hover">
            <div class="stat-num highlight">{{ stats?.todayPromptCount ?? '-' }}</div>
            <div class="stat-label"><el-icon><TrendCharts /></el-icon> 今日新增</div>
          </el-card>
        </div>
        <el-card class="panel" shadow="never">
          <template #header><span class="panel-title">系统说明</span></template>
          <div class="intro">
            <p>本系统提供四类管理员能力：<b>概览</b>查看关键指标；<b>用户管理</b>启停账号；<b>内容管理</b>审核、删除与导出；<b>权限管理</b>维护角色（用户分组）与权限点（授权）。</p>
            <p>所有管理接口均经过 Sa-Token 的 <code>@SaCheckRole("ADMIN")</code> 拦截，权限点的细粒度校验通过 <code>StpInterfaceImpl</code> 从数据库动态加载。</p>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 2. 用户管理 -->
      <el-tab-pane label="用户管理" name="users">
        <el-card class="panel" shadow="never">
          <template #header><span class="panel-title">用户管理</span></template>
          <el-table v-loading="userLoading" :data="userList" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
            <el-table-column label="角色" width="120">
              <template #default="{ row }">
                <el-tag :type="roleTagType(row.role)" size="small">{{ row.role }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '正常' : '已禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="注册时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canToggleStatus(row)" size="small"
                  :type="row.status === 1 ? 'danger' : 'success'"
                  @click="toggleUserStatus(row)">
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination
              v-model:current-page="userPage.pageNum"
              :page-size="userPage.pageSize"
              :total="userTotal"
              layout="total, prev, pager, next"
              @current-change="loadUsers"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 3. 内容管理 -->
      <el-tab-pane label="内容管理" name="content">
        <el-card class="panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">内容管理</span>
              <div class="panel-toolbar">
                <el-input v-model="promptQuery.keyword" placeholder="输入后表格自动筛选，回车立即搜索" clearable
                  class="panel-search" @keyup.enter="handlePromptSearch" @clear="handlePromptSearch">
                  <template #append>
                    <el-button @click="handlePromptSearch">
                      <el-icon><Search /></el-icon>搜索
                    </el-button>
                  </template>
                </el-input>
                <el-button type="primary" @click="handleExport">
                  <el-icon><Download /></el-icon>导出数据
                </el-button>
              </div>
            </div>
          </template>
          <el-table v-loading="promptLoading" :data="promptList" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="link" @click="router.push(`/prompt/${row.id}`)">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="categoryName" label="分类" width="110" />
            <el-table-column prop="username" label="作者" width="110" />
            <el-table-column prop="viewCount" label="浏览" width="80" />
            <el-table-column prop="favoriteCount" label="收藏" width="80" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="danger" @click="handleDeletePrompt(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination
              v-model:current-page="promptQuery.pageNum"
              :page-size="promptQuery.pageSize"
              :total="promptTotal"
              layout="total, prev, pager, next"
              @current-change="loadPrompts"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 4. 分类管理 -->
      <el-tab-pane label="分类管理" name="category">
        <el-card class="panel" shadow="never">
          <template #header><span class="panel-title">新增分类</span></template>
          <div class="cat-form">
            <el-input
              v-model="categoryForm.name"
              placeholder="分类名称（必填，不超过 50 字）"
              maxlength="50" clearable class="cat-input"
              @keyup.enter="handleAddCategory"
            />
            <el-input
              v-model="categoryForm.description"
              placeholder="分类描述（可选，不超过 200 字）"
              maxlength="200" clearable class="cat-input"
              @keyup.enter="handleAddCategory"
            />
            <el-button type="primary" :loading="categorySaving" @click="handleAddCategory">
              <el-icon><Plus /></el-icon>新增分类
            </el-button>
          </div>
        </el-card>

        <el-card class="panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">分类列表（{{ categoryList.length }} 个）</span>
              <span class="rbac-hint">分类下还有 Prompt 时不允许删除，需先迁移或删除这些 Prompt</span>
            </div>
          </template>
          <el-table v-loading="categoryLoading" :data="categoryList" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="分类名称" width="180" />
            <el-table-column prop="description" label="描述" min-width="280" show-overflow-tooltip>
              <template #default="{ row }">{{ row.description || '—' }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="danger" @click="handleDeleteCategory(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 5. 权限管理（RBAC） -->
      <el-tab-pane label="权限管理" name="rbac">
        <el-card class="panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">角色 × 权限（用户分组、授权、权限维护）</span>
              <span class="rbac-hint">点击「分配权限」调整该角色可使用的功能，保存后立即生效（每次鉴权实时查库）</span>
            </div>
          </template>
          <el-table v-loading="rbacLoading" :data="roleList" stripe>
            <el-table-column prop="code" label="角色编码" width="160" />
            <el-table-column prop="name" label="角色名称" width="120" />
            <el-table-column prop="description" label="角色描述" min-width="240" show-overflow-tooltip />
            <el-table-column label="已分配权限" min-width="280">
              <template #default="{ row }">
                <el-tag v-for="pc in row.permissionCodes" :key="pc" size="small" type="info" class="perm-tag">{{ pc }}</el-tag>
                <span v-if="!row.permissionCodes?.length" class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.code === 'SUPER_ADMIN'" @click="openAssignDialog(row)">分配权限</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="panel" shadow="never" style="margin-top: 16px;">
          <template #header><span class="panel-title">权限点字典（{{ permList.length }} 个）</span></template>
          <el-table :data="permList" stripe size="small">
            <el-table-column prop="code" label="权限编码" width="220" />
            <el-table-column prop="name" label="权限名称" width="120" />
            <el-table-column prop="module" label="所属模块" width="120">
              <template #default="{ row }">{{ moduleNames[row.module] || row.module }}</template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="300" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 权限分配弹窗 -->
    <el-dialog
      v-model="rbacDialog.visible"
      :title="`为「${rbacDialog.role?.name}」分配权限`"
      width="640px"
      :close-on-click-modal="false"
    >
      <div v-if="rbacDialog.role" class="rbac-dialog">
        <p class="rbac-desc">勾选该角色可使用的权限点。已选 <b>{{ rbacDialog.draft.length }}</b> / 共 {{ permList.length }} 个权限。</p>
        <el-checkbox-group v-model="rbacDialog.draft" class="rbac-group">
          <div v-for="(perms, mod) in permByModule()" :key="mod" class="rbac-module">
            <div class="rbac-module-title">{{ moduleNames[mod] || mod }}（{{ perms.length }}）</div>
            <el-checkbox v-for="p in perms" :key="p.code" :value="p.code" :label="p.code">
              <span class="rbac-cb">
                <b>{{ p.name }}</b>
                <span class="rbac-cb-desc">{{ p.description }}</span>
              </span>
            </el-checkbox>
          </div>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="rbacDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="rbacDialog.saving" @click="saveAssign">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-tabs { margin-bottom: 8px; }
.stats-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; text-align: center; }
.stat-card:hover { box-shadow: var(--app-shadow-hover); }
.stat-num { font-size: 28px; font-weight: bold; color: var(--app-brand); }
.stat-num.highlight { color: var(--app-accent); }
.stat-label {
  display: inline-flex; align-items: center; gap: 4px;
  color: var(--app-text-secondary); font-size: 13px; margin-top: 4px;
}
.panel { margin-bottom: 16px; }
.panel-title { font-weight: bold; color: var(--app-text-primary); }
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.panel-toolbar { display: flex; align-items: center; gap: 10px; }
.panel-search { width: 240px; }
.link { cursor: pointer; color: var(--app-brand); }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
.intro p { margin: 4px 0; color: var(--app-text-secondary); }
.intro code {
  background: var(--app-bg-soft); padding: 2px 6px; border-radius: 4px;
  font-size: 12px; color: var(--app-brand);
}

.rbac-hint { color: var(--app-text-secondary); font-size: 12px; }
.perm-tag { margin: 2px 4px 2px 0; }
.muted { color: var(--app-text-secondary); }

.cat-form { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cat-input { width: 260px; }

.rbac-dialog .rbac-desc { color: var(--app-text-secondary); margin: 0 0 12px; }
.rbac-group { display: flex; flex-direction: column; gap: 14px; }
.rbac-module { padding: 10px 12px; background: var(--app-bg-soft); border-radius: 6px; }
.rbac-module-title { font-weight: bold; margin-bottom: 8px; color: var(--app-text-primary); }
.rbac-cb { display: inline-flex; flex-direction: column; line-height: 1.4; margin-left: 4px; }
.rbac-cb-desc { font-size: 11px; color: var(--app-text-secondary); }

/* —— 分配权限对话框复选框配色（无边框：白天黑字黑底白勾，夜晚白字白底深勾）—— */
/* 1) 去掉权限卡片边框，改为纵向清单 */
.rbac-module { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.rbac-dialog :deep(.el-checkbox) { height: auto; margin-right: 0; }

/* 2) 白天：未选 = 白底灰边；选中 = 黑底白勾、文字黑色（不变蓝） */
.rbac-dialog :deep(.el-checkbox__inner) {
  background-color: #ffffff;
  border: 2px solid #b8b4a6;
}
.rbac-dialog :deep(.el-checkbox__input.is-checked .el-checkbox__inner),
.rbac-dialog :deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner) {
  background-color: #2f3327;
  border-color: #2f3327;
}
.rbac-dialog :deep(.el-checkbox__input.is-checked .el-checkbox__inner::after) { border-color: #ffffff; }
.rbac-dialog :deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner::before) { background: #ffffff; }
.rbac-dialog :deep(.el-checkbox.is-checked .el-checkbox__label) { color: #1f1f1f; }

/* 3) 夜晚：未选 = 深底亮边（和深色卡片区分开）；选中 = 白底深勾、文字白色（不变蓝） */
html.dark .rbac-dialog :deep(.el-checkbox__inner) {
  background-color: rgba(13, 11, 20, 0.9);
  border: 2px solid rgba(255, 255, 255, 0.45);
}
html.dark .rbac-dialog :deep(.el-checkbox__input.is-checked .el-checkbox__inner),
html.dark .rbac-dialog :deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner) {
  background-color: #ffffff;
  border-color: #ffffff;
}
html.dark .rbac-dialog :deep(.el-checkbox__input.is-checked .el-checkbox__inner::after) { border-color: #0d0b14; }
html.dark .rbac-dialog :deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner::before) { background: #0d0b14; }
html.dark .rbac-dialog :deep(.el-checkbox.is-checked .el-checkbox__label) { color: #ffffff; }
</style>
