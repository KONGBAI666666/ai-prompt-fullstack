<script setup>
// Prompt 详情页：GET /api/prompt/{id}（后端同时给浏览次数+1）
// + AI 试运行面板：POST /api/ai/run（SSE 流式，打字机效果）
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPromptDetail, deletePrompt } from '@/api/prompt'
import { runAi } from '@/api/ai'
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

// —— AI 试运行状态 ——
const aiInput = ref('')      // 用户的补充输入
const aiOutput = ref('')     // 已生成的文本（逐块追加，打字机效果）
const aiRunning = ref(false) // 生成中（控制按钮切换 / 输出区光标）
let aiAbort = null           // AbortController，点"停止"时中断 fetch

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await getPromptDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
// 离开页面时若还在生成，主动断开连接（后端 emitter 会随连接关闭而终止）
onBeforeUnmount(() => aiAbort?.abort())

// 开始试运行：清空旧输出 → 建立流 → 每收到一段就追加渲染
function startRun() {
  if (aiRunning.value) return
  aiOutput.value = ''
  aiRunning.value = true
  aiAbort = new AbortController()
  runAi(
    detail.value.id,
    aiInput.value,
    {
      onChunk: (text) => { aiOutput.value += text },
      onDone: () => { aiRunning.value = false; ElMessage.success('生成完成，已计入使用记录') },
      onError: (msg) => { aiRunning.value = false; ElMessage.error(msg) },
    },
    aiAbort.signal,
  )
}

// 停止生成：中断 fetch 流（AbortSignal），已生成的内容保留在屏幕上
function stopRun() {
  aiAbort?.abort()
  aiRunning.value = false
}


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

      <!-- AI 试运行面板：填入补充输入 → 大模型按这条提示词生成 → 打字机流式输出 -->
      <div class="ai-panel">
        <div class="ai-panel-title">
          <el-icon><MagicStick /></el-icon>
          AI 试运行
          <span class="ai-panel-hint">填入你的内容，AI 将按这条提示词处理（每日 20 次）</span>
        </div>
        <el-input
          v-model="aiInput"
          type="textarea"
          :rows="3"
          :disabled="aiRunning"
          placeholder="补充输入（可选）：比如要让 AI 处理的原文、主题或问题"
          maxlength="2000"
          show-word-limit
          class="ai-input"
        />
        <div class="ai-actions">
          <el-button
            type="primary"
            :loading="aiRunning"
            :disabled="!detail"
            @click="startRun"
          >
            <el-icon v-if="!aiRunning"><CaretRight /></el-icon>
            {{ aiRunning ? '生成中…' : '开始生成' }}
          </el-button>
          <el-button v-if="aiRunning" @click="stopRun">
            <el-icon><VideoPause /></el-icon>停止
          </el-button>
        </div>
        <div v-if="aiOutput || aiRunning" class="ai-output-wrap">
          <pre class="ai-output">{{ aiOutput }}<span v-if="aiRunning" class="cursor" /></pre>
        </div>
      </div>
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

/* —— AI 试运行面板 —— */
.ai-panel {
  margin-top: 24px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-card-bg);
}

.ai-panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: bold;
  font-size: 15px;
  color: var(--app-text-primary);
  margin-bottom: 12px;
}

.ai-panel-hint {
  font-weight: normal;
  font-size: 12px;
  color: var(--app-text-secondary);
  margin-left: 4px;
}

.ai-input {
  margin-bottom: 12px;
}

.ai-actions {
  display: flex;
  gap: 8px;
}

.ai-output-wrap {
  margin-top: 12px;
}

.ai-output {
  background: var(--app-code-bg);
  border-radius: 6px;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--app-text-primary);
  min-height: 60px;
  margin: 0;
}

/* 生成中的打字机光标 */
.cursor {
  display: inline-block;
  width: 8px;
  height: 16px;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--app-text-primary);
  animation: blink 0.9s steps(1) infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
