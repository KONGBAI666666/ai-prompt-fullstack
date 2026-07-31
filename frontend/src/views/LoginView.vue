<script setup>
// 登录页：项目的第一个完整前后端联调页面
// 链路：本页面 → api/user.js → api/request.js → POST /api/user/login → 后端
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/user'
import { setToken, setUser } from '@/utils/auth'

const router = useRouter()

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
    <el-card class="login-card">
      <h2 class="title">AI 提示词管理平台</h2>
      <p class="subtitle">分享、检索、收藏你的 AI Prompt</p>

      <el-form :model="form" @keyup.enter="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" clearable />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
          />
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
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  padding: 16px 8px;
}

.title {
  text-align: center;
  margin-bottom: 4px;
}

.subtitle {
  text-align: center;
  color: #909399;
  font-size: 13px;
  margin-bottom: 24px;
}

.login-btn {
  width: 100%;
}

.tip {
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
