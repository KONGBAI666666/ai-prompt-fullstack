// Axios 统一封装：所有接口请求都走这里
// 职责：①拼接 baseURL ②自动携带 token ③统一处理后端 Result 结构
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '@/utils/auth'

const request = axios.create({
  // 开发期 Vite 把 /api 代理到 localhost:8080，见 vite.config.js
  baseURL: '/api',
  timeout: 10000,
})

// 请求拦截器：每次请求前自动把 token 塞进请求头
// 后端 Sa-Token 配置 token-name: Authorization（application-dev.yml）
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

// 响应拦截器：统一拆后端 Result{code, message, data}
// 成功时直接返回 data，页面里拿到的就是业务数据本身，不用再写 res.data.data
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      // 401：未登录或 token 过期（后端 SaTokenConfig 过滤器返回）
      if (res.code === 401) {
        clearAuth()
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (error) => {
    // HTTP 层错误（网络断开、后端没启动、500等）
    const msg = error.response?.data?.message || '网络异常，请检查后端是否启动'
    ElMessage.error(msg)
    if (error.response?.status === 401) {
      clearAuth()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default request
