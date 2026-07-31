// 用户模块接口，对应后端 UserController
import request from './request'

// 注册：{username, password, nickname?}
export function register(data) {
  return request.post('/user/register', data)
}

// 登录：{username, password}，返回 {token, user}
export function login(data) {
  return request.post('/user/login', data)
}

// 当前登录用户信息，返回 UserVO
export function getUserInfo() {
  return request.get('/user/info')
}

// 退出登录
export function logout() {
  return request.post('/user/logout')
}
