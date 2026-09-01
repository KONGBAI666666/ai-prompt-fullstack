<script setup>
// 管理员后台：4 个标签页
// 1) 概览   — 系统统计卡片
// 2) 用户管理 — 用户列表（禁用/启用）
// 3) 内容管理 — Prompt 列表 + 关键词搜索 + 数据导出
// 4) 权限管理 — 角色 × 权限点（RBAC 权限维护子系统）
// 后端所有管理类接口均带 @SaCheckRole("ADMIN") / @SaCheckPermission
import { ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getStats, getAdminUserList, updateUserStatus, getAdminPromptList, exportPrompts } from '@/api/admin'
import { deletePrompt } from '@/api/prompt'
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
  const blob = await exportPrompts(promptQuery.value.keyword || undefined)
  const url = URL.createObjectURL(new Blob([blob], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  const pad = (n) => String(n).padStart(2, '0')
  const t = new Date()
  a.download = `Prompt数据_${t.getFullYear()}${pad(t.getMonth() + 1)}${pad(t.getDate())}${pad(t.getHours())}${pad(t.getMinutes())}${pad(t.getSeconds())}.csv`
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
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
            <el-table-column label="角色" width="100">
              <template #default="{ row }">
                <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
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
                <el-button v-if="row.role !== 'ADMIN'" size="small"
                  :type="row.status === 1 ? 'danger' : 'success'"
                  @click="toggleUserStatus(row)">
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
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
                <el-input v-model="promptQuery.keyword" placeholder="搜索标题或描述" clearable
                  class="panel-search" @keyup.enter="handlePromptSearch" @clear="handlePromptSearch" />
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

      <!-- 4. 权限管理（RBAC） -->
      <el-tab-pane label="权限管理" name="rbac">
        <el-card class="panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">角色 × 权限（用户分组、授权、权限维护）</span>
              <span class="rbac-hint">点击「分配权限」调整该角色可使用的功能，变更后下次登录生效</span>
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
            <el-checkbox v-for="p in perms" :key="p.code" :value="p.code" :label="p.code" border>
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

.rbac-dialog .rbac-desc { color: var(--app-text-secondary); margin: 0 0 12px; }
.rbac-group { display: flex; flex-direction: column; gap: 14px; }
.rbac-module { padding: 10px 12px; background: var(--app-bg-soft); border-radius: 6px; }
.rbac-module-title { font-weight: bold; margin-bottom: 8px; color: var(--app-text-primary); }
.rbac-cb { display: inline-flex; flex-direction: column; line-height: 1.4; margin-left: 4px; }
.rbac-cb-desc { font-size: 11px; color: var(--app-text-secondary); }
</style>
