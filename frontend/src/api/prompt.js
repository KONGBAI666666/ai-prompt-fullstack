// Prompt 模块接口，对应后端 PromptController
import request from './request'

// 分页搜索列表：params = {pageNum, pageSize, keyword?, categoryId?}
// 返回 MyBatis-Plus Page：{records, total, current, size, pages}
export function getPromptList(params) {
  return request.get('/prompt/list', { params })
}

// 我的 Prompt 分页列表：params = {pageNum, pageSize}
export function getMyPrompts(params) {
  return request.get('/prompt/my', { params })
}

// 详情（后端会给浏览次数+1）
export function getPromptDetail(id) {
  return request.get(`/prompt/${id}`)
}

// 创建：{title, content, description?, categoryId}
export function createPrompt(data) {
  return request.post('/prompt', data)
}

// 修改自己的 Prompt
export function updatePrompt(id, data) {
  return request.put(`/prompt/${id}`, data)
}

// 删除（本人或管理员）
export function deletePrompt(id) {
  return request.delete(`/prompt/${id}`)
}
