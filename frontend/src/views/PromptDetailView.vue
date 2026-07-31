<script setup>
// Prompt 详情页：CRUD 中的 R（单条）
// 路由 /prompt/:id → 本页面 → GET /api/prompt/{id}（后端同时给浏览次数+1）
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPromptDetail } from '@/api/prompt'
import { addFavorite, cancelFavorite } from '@/api/favorite'
import { recordHistory } from '@/api/history'

// useRoute()：拿当前路由信息，route.params.id 就是地址栏里的那个 :id
const route = useRoute()
const router = useRouter()

const detail = ref(null) // PromptVO，加载完成前为 null
const loading = ref(false)

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await getPromptDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)

async function toggleFavorite() {
  if (detail.value.favorited) {
    await cancelFavorite(detail.value.id)
    detail.value.favorited = false
    detail.value.favoriteCount--
    ElMessage.success('已取消收藏')
  } else {
    await addFavorite(detail.value.id)
    detail.value.favorited = true
    detail.value.favoriteCount++
    ElMessage.success('收藏成功')
  }
}

async function copyPrompt() {
  await navigator.clipboard.writeText(detail.value.content)
  ElMessage.success('已复制到剪贴板')
  // 记录一次使用，失败不影响复制体验
  recordHistory(detail.value.id).catch(() => {})
}
</script>

<template>
  <div v-loading="loading">
    <el-card v-if="detail">
      <template #header>
        <div class="detail-header">
          <div class="title-line">
            <el-button link @click="router.back()">← 返回</el-button>
            <span class="title">{{ detail.title }}</span>
            <el-tag v-if="detail.categoryName" size="small">{{ detail.categoryName }}</el-tag>
          </div>
          <div class="actions">
            <el-button
              :type="detail.favorited ? 'warning' : 'default'"
              @click="toggleFavorite"
            >
              {{ detail.favorited ? '⭐ 已收藏' : '☆ 收藏' }} {{ detail.favoriteCount }}
            </el-button>
            <el-button type="primary" @click="copyPrompt">复制使用</el-button>
          </div>
        </div>
      </template>

      <div class="meta">
        👤 {{ detail.username }}　👁 {{ detail.viewCount }} 次浏览
        <span v-if="detail.createTime">　🕐 {{ detail.createTime.replace('T', ' ') }}</span>
      </div>

      <p class="desc">{{ detail.description || '暂无描述' }}</p>

      <div class="content-label">Prompt 内容</div>
      <pre class="content">{{ detail.content }}</pre>
    </el-card>
  </div>
</template>

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.title-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title {
  font-size: 18px;
  font-weight: bold;
}

.meta {
  color: #909399;
  font-size: 13px;
  margin-bottom: 12px;
}

.desc {
  color: #606266;
  font-size: 14px;
  margin-bottom: 16px;
}

.content-label {
  font-weight: bold;
  font-size: 14px;
  margin-bottom: 8px;
}

/* 详情页正文完整展示，不限高度 */
.content {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  color: #303133;
}
</style>
