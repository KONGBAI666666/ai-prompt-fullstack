// 使用记录模块接口，对应后端 HistoryController
import request from './request'

// 记录一次使用（"复制Prompt"时调用）
export function recordHistory(promptId) {
  return request.post(`/history/${promptId}`)
}

// 我的使用记录分页列表：params = {pageNum, pageSize}
export function getHistoryList(params) {
  return request.get('/history/list', { params })
}
