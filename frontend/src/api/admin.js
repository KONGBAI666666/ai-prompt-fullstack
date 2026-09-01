// 管理员模块接口，对应后端 AdminController（全部需要 ADMIN 角色）
import request from './request'

// 用户分页列表：params = {pageNum, pageSize}
export function getAdminUserList(params) {
  return request.get('/admin/user/list', { params })
}

// 启用/禁用用户：status 1正常 0禁用（后端禁用后会踢下线）
export function updateUserStatus(id, status) {
  return request.put(`/admin/user/${id}/status`, null, { params: { status } })
}

// 所有 Prompt 分页（管理员视角）：params = {pageNum, pageSize, keyword?}
export function getAdminPromptList(params) {
  return request.get('/admin/prompt/list', { params })
}

// 系统统计：{userCount, promptCount, favoriteCount, todayPromptCount}
export function getStats() {
  return request.get('/admin/stats')
}

// 导出 Prompt 查询结果为 CSV 文件（数据转储），可按关键词过滤
export function exportPrompts(keyword) {
  return request.get('/admin/prompt/export', {
    params: keyword ? { keyword } : {},
    responseType: 'blob',
  })
}
