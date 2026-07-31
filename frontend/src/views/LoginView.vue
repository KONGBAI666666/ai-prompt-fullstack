<script setup>
// 登录页：项目的第一个完整前后端联调页面
// 链路：本页面 → api/user.js → api/request.js → POST /api/user/login → 后端
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/user'
import { setToken, setUser } from '@/utils/auth'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const { isDark, toggleTheme } = useTheme()

// ref()：把普通值变成响应式数据，模板里用到它的地方会随值变化自动刷新
const form = ref({
  username: '',
  password: '',
})
const loading = ref(false)

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    // request.js 的响应拦截器已经拆过 Result，这里拿到的直接是 LoginVO：{token, user}
    const data = await login(form.value)
    setToken(data.token)
    setUser(data.user)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    // 无论成败都恢复按钮（失败的错误提示由拦截器统一弹出）
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 右上角主题切换 -->
    <el-button circle text class="theme-btn" @click="toggleTheme">
      <el-icon :size="20"><Sunny v-if="isDark" /><Moon v-else /></el-icon>
    </el-button>

    <div class="login-panel">
      <!-- 左侧品牌区：背景由 --app-login-hero 随主题切换（明面米绿渐变/暗面紫色光晕） -->
      <div class="hero">
        <div class="deco deco-1"></div>
        <div class="deco deco-2"></div>
        <div class="hero-brand">
          <el-icon :size="44"><MagicStick /></el-icon>
          <div class="hero-title">AI 提示词管理平台</div>
          <div class="hero-sub">分享、检索、收藏你的 AI Prompt</div>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-side">
        <h2 class="title">欢迎回来</h2>
        <p class="subtitle">登录你的账号</p>

        <el-form :model="form" @keyup.enter="handleLogin">
          <el-form-item>
            <el-input v-model="form.username" placeholder="用户名" size="large" clearable>
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              show-password
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <p class="tip">测试账号：test / 123456　管理员：admin / admin123</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--app-bg);
  position: relative;
}

.theme-btn {
  position: absolute;
  top: 20px;
  right: 24px;
  color: var(--app-text-secondary);
}

/* 左右分栏面板：参考图的结构 */
.login-panel {
  width: 860px;
  max-width: calc(100vw - 32px);
  min-height: 480px;
  display: flex;
  border-radius: 16px;
  overflow: hidden;
  background: var(--app-card-bg);
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow);
}

.hero {
  flex: 1.1;
  position: relative;
  background: var(--app-login-hero);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  overflow: hidden;
}

/* 纯 CSS 装饰圆环，替代参考图的照片素材，无版权问题 */
.deco {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.35);
}

.deco-1 {
  width: 220px;
  height: 220px;
  top: -60px;
  left: -60px;
}

.deco-2 {
  width: 140px;
  height: 140px;
  bottom: -30px;
  right: -20px;
  background: rgba(255, 255, 255, 0.08);
  border: none;
}

.hero-brand {
  text-align: center;
  z-index: 1;
}

.hero-title {
  font-family: var(--app-title-font);
  font-size: 22px;
  font-weight: bold;
  margin-top: 14px;
}

.hero-sub {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 8px;
}

.form-side {
  flex: 1;
  padding: 48px 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.title {
  font-family: var(--app-title-font);
  color: var(--app-text-primary);
  margin-bottom: 4px;
}

.subtitle {
  color: var(--app-text-secondary);
  font-size: 13px;
  margin-bottom: 28px;
}

.login-btn {
  width: 100%;
}

.tip {
  text-align: center;
  color: var(--app-text-secondary);
  font-size: 12px;
  margin-top: 8px;
}

/* 窄屏隐藏左侧品牌区，只留表单 */
@media (max-width: 640px) {
  .hero {
    display: none;
  }
}
</style>
