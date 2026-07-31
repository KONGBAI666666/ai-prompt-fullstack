// 收藏模块接口，对应后端 FavoriteController
import request from './request'

// 收藏 Prompt
export function addFavorite(promptId) {
  return request.post(`/favorite/${promptId}`)
}

// 取消收藏
export function cancelFavorite(promptId) {
  return request.delete(`/favorite/${promptId}`)
}

// 我的收藏分页列表：params = {pageNum, pageSize}
export function getFavoriteList(params) {
  return request.get('/favorite/list', { params })
}
