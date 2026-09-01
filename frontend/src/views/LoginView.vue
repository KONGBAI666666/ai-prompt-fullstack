<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register, getCaptcha } from '@/api/user'
import { setToken, setUser } from '@/utils/auth'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const { isDark, toggleTheme } = useTheme()

// 当前展示的表单模式：login=登录，register=注册
const mode = ref('login')

const form = ref({
  username: '',
  password: '',
  email: '',
  captchaId: '',
  captchaCode: '',
})
const captchaImg = ref('')
const loading = ref(false)

// 拉取一张新验证码（后端一次性校验，所以失败后必须刷新）
async function refreshCaptcha() {
  const data = await getCaptcha()
  form.value.captchaId = data.id
  captchaImg.value = data.image
  form.value.captchaCode = ''
}

onMounted(refreshCaptcha)

// 切换登录/注册表单，清空表单并刷新验证码
function switchMode(target) {
  mode.value = target
  form.value.username = ''
  form.value.password = ''
  form.value.email = ''
  refreshCaptcha()
}

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (!form.value.captchaCode) {
    ElMessage.warning('请输入验证码')
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
  } catch {
    // 验证码已一次性失效，无论哪种失败都刷新，避免用户拿旧码重试
    refreshCaptcha()
  } finally {
    // 无论成败都恢复按钮（失败的错误提示由拦截器统一弹出）
    loading.value = false
  }
}

async function handleRegister() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (form.value.password.length < 6 || form.value.password.length > 20) {
    ElMessage.warning('密码长度需在 6~20 位之间')
    return
  }
  if (form.value.email && !/^[\w.+-]+@[\w-]+\.[\w.]+$/.test(form.value.email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  loading.value = true
  try {
    await register({ username: form.value.username, password: form.value.password, email: form.value.email || null })
    ElMessage.success('注册成功，请登录')
    // 注册成功切回登录表单，保留用户名方便直接登录
    const keepUsername = form.value.username
    mode.value = 'login'
    form.value.password = ''
    form.value.email = ''
    form.value.username = keepUsername
    refreshCaptcha()
  } catch {
    // 注册失败（如用户名已存在），刷新验证码让用户重试
    refreshCaptcha()
  } finally {
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
        <h2 class="title">{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h2>
        <p class="subtitle">{{ mode === 'login' ? '登录你的账号' : '注册一个新账号' }}</p>

        <el-form :model="form" @keyup.enter="mode === 'login' ? handleLogin() : handleRegister()">
          <el-form-item>
            <el-input v-model="form.username" placeholder="用户名" size="large" clearable>
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码（6~20位）"
              size="large"
              show-password
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <!-- 注册模式才显示邮箱输入框（可选字段） -->
          <el-form-item v-if="mode === 'register'">
            <el-input v-model="form.email" placeholder="邮箱（可选）" size="large" clearable>
              <template #prefix><el-icon><Message /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <div class="captcha-row">
              <el-input
                v-model="form.captchaCode"
                placeholder="验证码"
                size="large"
                maxlength="4"
                class="captcha-input"
              >
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
              <img
                v-if="captchaImg"
                :src="captchaImg"
                alt="验证码"
                title="看不清？点击换一张"
                class="captcha-img"
                @click="refreshCaptcha"
              />
            </div>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="mode === 'login' ? handleLogin() : handleRegister()"
            >
              {{ mode === 'login' ? '登 录' : '注 册' }}
            </el-button>
          </el-form-item>
          <!-- 登录/注册切换 -->
          <div class="switch-mode">
            <span v-if="mode === 'login'">
              还没有账号？
              <el-link type="primary" :underline="false" @click="switchMode('register')">注册账号</el-link>
            </span>
            <span v-else>
              已有账号？
              <el-link type="primary" :underline="false" @click="switchMode('login')">返回登录</el-link>
            </span>
          </div>
        </el-form>
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

/* 左右分栏面板 */
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

/* 登录/注册切换链接 */
.switch-mode {
  text-align: center;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-input {
  flex: 1;
}

.captcha-img {
  height: 40px;
  width: 110px;
  border-radius: 6px;
  border: 1px solid var(--app-border);
  cursor: pointer;
  user-select: none;
}

/* 窄屏隐藏左侧品牌区，只留表单 */@media (max-width: 640px) {
  .hero {
    display: none;
  }
}
</style>
