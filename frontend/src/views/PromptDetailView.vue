<script setup>
// Prompt 详情页：GET /api/prompt/{id}（后端同时给浏览次数+1）
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPromptDetail, deletePrompt } from '@/api/prompt'
import { getUser } from '@/utils/auth'
import { formatTime } from '@/utils/format'
import { usePromptActions } from '@/composables/usePromptActions'

const route = useRoute()
const router = useRouter()
const { toggleFavorite, copyPrompt } = usePromptActions()

const detail = ref(null) // PromptVO，加载完成前为 null
const loading = ref(false)

// 登录时存的当前用户，用来判断按钮显示（仅体验层，后端 Service 会再次校验）
const currentUser = getUser()
const isOwner = computed(() => detail.value && currentUser?.id === detail.value.userId)
const isAdmin = currentUser?.role === 'ADMIN'

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await getPromptDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)

// 删除：二次确认 → DELETE /api/prompt/{id} → 回列表
// 后端会再次校验"本人或管理员"，前端按钮只是体验优化
async function handleDelete() {
  try {
    await ElMessageBox.confirm('删除后会同时清除相关收藏和使用记录，确定删除？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return // 用户取消删除
  }
  await deletePrompt(detail.value.id)
  ElMessage.success('删除成功')
  router.push('/')
}
</script>

<template>
  <div v-loading="loading">
    <el-card v-if="detail">
      <template #header>
        <div class="detail-header">
          <div class="title-line">
            <el-button link @click="router.back()">
              <el-icon><ArrowLeft /></el-icon>返回
            </el-button>
            <span class="title">{{ detail.title }}</span>
            <el-tag v-if="detail.categoryName" size="small">{{ detail.categoryName }}</el-tag>
          </div>
          <div class="actions">
            <el-button
              :type="detail.favorited ? 'warning' : 'default'"
              @click="toggleFavorite(detail)"
            >
              <el-icon><Star /></el-icon>
              {{ detail.favorited ? '已收藏' : '收藏' }} {{ detail.favoriteCount }}
            </el-button>
            <el-button type="primary" @click="copyPrompt(detail)">复制使用</el-button>
            <!-- 编辑仅本人；删除本人或管理员（与后端 Service 规则一致） -->
            <el-button v-if="isOwner" @click="router.push(`/prompt/edit/${detail.id}`)">编辑</el-button>
            <el-button v-if="isOwner || isAdmin" type="danger" @click="handleDelete">删除</el-button>
          </div>
        </div>
      </template>

      <div class="meta">
        <el-icon><User /></el-icon> {{ detail.username }}
        <el-icon><View /></el-icon> {{ detail.viewCount }} 次浏览
        <span v-if="detail.createTime">
          <el-icon><Clock /></el-icon> {{ formatTime(detail.createTime) }}
        </span>
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
  font-family: var(--app-title-font);
  font-size: 18px;
  font-weight: bold;
  color: var(--app-text-primary);
}

.meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  color: var(--app-text-secondary);
  font-size: 13px;
  margin-bottom: 12px;
}

.meta .el-icon {
  margin-left: 8px;
}

.meta span {
  display: inline-flex;
  align-items: center;
}

.desc {
  color: var(--app-text-secondary);
  font-size: 14px;
  margin-bottom: 16px;
}

.content-label {
  font-weight: bold;
  font-size: 14px;
  color: var(--app-text-primary);
  margin-bottom: 8px;
}

/* 详情页正文完整展示，不限高度 */
.content {
  background: var(--app-code-bg);
  border-radius: 6px;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--app-text-primary);
}
</style>
