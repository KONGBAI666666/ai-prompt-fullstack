<script setup>
// 个人中心：我的信息 + 我的Prompt / 我的收藏 / 使用记录（三个Tab共用一套分页逻辑）
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo } from '@/api/user'
import { getMyPrompts } from '@/api/prompt'
import { getFavoriteList } from '@/api/favorite'
import { getHistoryList } from '@/api/history'

const router = useRouter()
const userInfo = ref(null) // UserVO，从后端实时拉取（比localStorage新鲜）

const activeTab = ref('my') // 当前Tab：my | favorite | history
const list = ref([])
const total = ref(0)
const loading = ref(false)
const page = ref({ pageNum: 1, pageSize: 10 })

// 三个Tab对应三个接口，返回的都是 MyBatis-Plus Page 结构
const loaders = {
  my: getMyPrompts,
  favorite: getFavoriteList,
  history: getHistoryList,
}

async function loadList() {
  loading.value = true
  try {
    const data = await loaders[activeTab.value](page.value)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 切换Tab回到第一页重查
function handleTabChange() {
  page.value.pageNum = 1
  loadList()
}

onMounted(async () => {
  loadList()
  userInfo.value = await getUserInfo()
})

// 我的Prompt/收藏列表点击跳详情；使用记录用 promptId
function goDetail(item) {
  router.push(`/prompt/${item.promptId || item.id}`)
}

function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 19) : ''
}
</script>

<template>
  <div>
    <!-- 我的信息卡片 -->
    <el-card class="info-card" shadow="never">
      <div v-if="userInfo" class="info-row">
        <div class="avatar">{{ (userInfo.username || '?')[0].toUpperCase() }}</div>
        <div>
          <div class="name-line">
            <span class="username">{{ userInfo.username }}</span>
            <el-tag v-if="userInfo.role === 'ADMIN'" type="danger" size="small">管理员</el-tag>
          </div>
          <div class="sub">
            <span v-if="userInfo.email"><el-icon><Message /></el-icon> {{ userInfo.email }}　</span>
            <span><el-icon><Clock /></el-icon> 注册于 {{ formatTime(userInfo.createTime) }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 三个列表Tab -->
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="我的 Prompt" name="my" />
        <el-tab-pane label="我的收藏" name="favorite" />
        <el-tab-pane label="使用记录" name="history" />
      </el-tabs>

      <div v-loading="loading">
        <el-empty v-if="!loading && list.length === 0" description="暂无数据" />

        <!-- 使用记录：字段是 HistoryVO {promptTitle, useTime} -->
        <template v-if="activeTab === 'history'">
          <div v-for="item in list" :key="item.id" class="row" @click="goDetail(item)">
            <span class="row-title">{{ item.promptTitle }}</span>
            <span class="row-meta">{{ formatTime(item.useTime) }}</span>
          </div>
        </template>

        <!-- 我的Prompt / 我的收藏：字段是 PromptVO -->
        <template v-else>
          <div v-for="item in list" :key="item.id" class="row" @click="goDetail(item)">
            <span class="row-title">
              {{ item.title }}
              <el-tag v-if="item.categoryName" size="small">{{ item.categoryName }}</el-tag>
            </span>
            <span class="row-meta">
              <el-icon><View /></el-icon> {{ item.viewCount }}
              <el-icon><Star /></el-icon> {{ item.favoriteCount }}
            </span>
          </div>
        </template>
      </div>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page.pageNum"
          v-model:page-size="page.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadList"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.info-card {
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 无头像图时用首字母圆形占位，用品牌色 */
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--app-brand);
  color: #fff;
  font-size: 24px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
}

.name-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.username {
  font-size: 18px;
  font-weight: bold;
  color: var(--app-text-primary);
}

.sub {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.sub span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 4px;
  border-bottom: 1px solid var(--app-border);
  cursor: pointer;
}

.row:hover .row-title {
  color: var(--app-brand);
}

.row-title {
  font-size: 14px;
  color: var(--app-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.row-meta .el-icon {
  margin-left: 6px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
