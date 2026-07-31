<script setup>
// 管理员后台：数据统计 + 用户管理（禁用/启用）+ 内容管理（查看全部/删除违规）
// 后端 AdminController 全部接口带 @SaCheckRole("ADMIN")，非管理员调用直接被拒
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getStats, getAdminUserList, updateUserStatus, getAdminPromptList } from '@/api/admin'
import { deletePrompt } from '@/api/prompt'

const router = useRouter()

// ---------- 统计卡片 ----------
const stats = ref(null) // {userCount, promptCount, favoriteCount, todayPromptCount}

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

// 禁用/启用：后端禁用后会把该用户踢下线
async function toggleUserStatus(user) {
  const disable = user.status === 1
  await ElMessageBox.confirm(
    disable ? `禁用后 ${user.username} 会被立即踢下线，确定禁用？` : `确定恢复 ${user.username} 的使用权限？`,
    disable ? '禁用确认' : '启用确认',
    { type: 'warning' },
  )
  await updateUserStatus(user.id, disable ? 0 : 1)
  user.status = disable ? 0 : 1
  ElMessage.success(disable ? '已禁用并踢下线' : '已启用')
}

// ---------- Prompt 管理 ----------
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

// 删除违规 Prompt：复用通用删除接口（后端允许管理员删任何人的）
async function handleDeletePrompt(item) {
  await ElMessageBox.confirm(`确定删除「${item.title}」？相关收藏和使用记录会一并清除`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger',
  })
  await deletePrompt(item.id)
  ElMessage.success('删除成功')
  loadPrompts()
  stats.value = await getStats() // 删除后刷新统计
}

onMounted(async () => {
  loadUsers()
  loadPrompts()
  stats.value = await getStats()
})
</script>

<template>
  <div>
    <!-- 统计卡片 -->
    <div class="stats-row">
      <el-card class="stat-card" shadow="hover">
        <div class="stat-num">{{ stats?.userCount ?? '-' }}</div>
        <div class="stat-label">👥 用户总数</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-num">{{ stats?.promptCount ?? '-' }}</div>
        <div class="stat-label">📝 Prompt 总数</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-num">{{ stats?.favoriteCount ?? '-' }}</div>
        <div class="stat-label">⭐ 收藏总数</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-num highlight">{{ stats?.todayPromptCount ?? '-' }}</div>
        <div class="stat-label">🔥 今日新增</div>
      </el-card>
    </div>

    <!-- 用户管理 -->
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
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <!-- 后端禁止禁用管理员，前端同步隐藏按钮 -->
            <el-button
              v-if="row.role !== 'ADMIN'"
              size="small"
              :type="row.status === 1 ? 'danger' : 'success'"
              @click="toggleUserStatus(row)"
            >
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

    <!-- Prompt 管理 -->
    <el-card class="panel" shadow="never">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">内容管理</span>
          <el-input
            v-model="promptQuery.keyword"
            placeholder="搜索标题或描述"
            clearable
            class="panel-search"
            @keyup.enter="handlePromptSearch"
            @clear="handlePromptSearch"
          />
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
  </div>
</template>

<style scoped>
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  flex: 1;
  text-align: center;
}

.stat-num {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-num.highlight {
  color: #f56c6c;
}

.stat-label {
  color: #909399;
  font-size: 13px;
  margin-top: 4px;
}

.panel {
  margin-bottom: 16px;
}

.panel-title {
  font-weight: bold;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-search {
  width: 240px;
}

.link {
  cursor: pointer;
  color: #409eff;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}
</style>
