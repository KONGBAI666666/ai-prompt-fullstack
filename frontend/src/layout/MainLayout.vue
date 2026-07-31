<script setup>
// 主布局：顶部导航栏 + 内容区。登录后的所有页面都套在这个壳里
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { logout } from '@/api/user'
import { getUser, clearAuth } from '@/utils/auth'

const router = useRouter()
// 登录时存进 localStorage 的用户信息（LoginVO.user）
const user = getUser()

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
        <div class="brand">🚀 AI 提示词管理平台</div>
        <div class="right">
          <span class="welcome">
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
.navbar {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
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
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
}

.right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.welcome {
  color: #606266;
  font-size: 14px;
}

.content {
  max-width: 1100px;
  margin: 20px auto;
  padding: 0 16px;
}
</style>
