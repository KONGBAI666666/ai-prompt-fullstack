// 分类模块接口，对应后端 CategoryController
import request from './request'

// 分类列表（登录可见）
export function getCategoryList() {
  return request.get('/category/list')
}

// 新增分类（仅管理员）：{name, description?}
export function addCategory(data) {
  return request.post('/category', data)
}

// 删除分类（仅管理员）
export function deleteCategory(id) {
  return request.delete(`/category/${id}`)
}
