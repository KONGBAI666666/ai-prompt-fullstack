<script setup>
// 主布局：顶部导航栏 + 内容区。登录后的所有页面都套在这个壳里
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { logout } from '@/api/user'
import { getUser, clearAuth } from '@/utils/auth'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
// 登录时存进 localStorage 的用户信息（LoginVO.user）
const user = getUser()
const { isDark, toggleTheme } = useTheme()

async function handleLogout() {
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  try {
    // 通知后端注销会话（即使失败也继续清本地）
    await logout()
  } finally {
    clearAuth()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<template>
  <div class="layout">
    <header class="navbar">
      <div class="navbar-inner">
        <div class="brand" @click="router.push('/')">
          <el-icon :size="20"><MagicStick /></el-icon>
          <span>AI 提示词管理平台</span>
        </div>
        <div class="right">
          <!-- 明/暗主题切换 -->
          <el-button
            circle
            text
            class="theme-btn"
            :title="isDark ? '切换到明亮模式' : '切换到暗黑模式'"
            @click="toggleTheme"
          >
            <el-icon :size="17"><Sunny v-if="isDark" /><Moon v-else /></el-icon>
          </el-button>
          <!-- 管理后台入口：仅管理员可见（后端接口另有 @SaCheckRole 兼底） -->
          <el-button v-if="user?.role === 'ADMIN'" link type="primary" @click="router.push('/admin')">
            管理后台
          </el-button>
          <span class="welcome" @click="router.push('/profile')" title="个人中心">
            {{ user?.nickname || user?.username }}
            <el-tag v-if="user?.role === 'ADMIN'" type="danger" size="small">管理员</el-tag>
          </span>
          <el-button link type="danger" @click="handleLogout">退出登录</el-button>
        </div>
      </div>
    </header>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
/* 顶栏：半透明毛玻璃吸顶，颜色由 --app-navbar-bg 随主题切换 */
.navbar {
  background: var(--app-navbar-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--app-border);
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-inner {
  max-width: 1100px;
  margin: 0 auto;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--app-title-font);
  font-size: 18px;
  font-weight: bold;
  color: var(--app-brand);
  cursor: pointer;
}

.right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.theme-btn {
  color: var(--app-text-secondary);
}

.welcome {
  color: var(--app-text-secondary);
  font-size: 14px;
  cursor: pointer;
}

.welcome:hover {
  color: var(--app-brand);
}

.content {
  max-width: 1100px;
  margin: 20px auto;
  padding: 0 16px;
}
</style>
