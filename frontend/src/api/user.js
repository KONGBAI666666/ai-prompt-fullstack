// 用户模块接口，对应后端 UserController
import request from './request'

// 图形验证码：返回 {id, image(Base64图片)}，登录时需回传 id + 用户输入的验证码
export function getCaptcha() {
  return request.get('/user/captcha')
}

// 登录：{username, password, captchaId, captchaCode}，返回 {token, user}
export function login(data) {
  return request.post('/user/login', data)
}

// 注册：{username, password, email?}，成功后需自行登录
export function register(data) {
  return request.post('/user/register', data)
}

// 当前登录用户信息，返回 UserVO
export function getUserInfo() {
  return request.get('/user/info')
}

// 退出登录
export function logout() {
  return request.post('/user/logout')
}
