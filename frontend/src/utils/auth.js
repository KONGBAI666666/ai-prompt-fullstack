// token 与用户信息的本地存储工具：统一管理 localStorage 的 key，避免各处散写字符串
const TOKEN_KEY = 'ai_prompt_token'
const USER_KEY = 'ai_prompt_user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  // localStorage 中的值可能损坏（被手动改动/插件覆盖），解析失败按未登录处理，避免路由守卫崩溃白屏
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function setUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

// 退出登录 / token失效时调用，清空所有登录痕迹
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
