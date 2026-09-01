// 权限管理子系统（RBAC）接口，对应后端 RbacController（全部需要 ADMIN 角色）
import request from './request'

// 角色列表（含每个角色已绑定的权限编码）：返回 [{code, name, description, permissionCodes: []}]
export function getRoleList() {
  return request.get('/admin/rbac/role/list')
}

// 权限点字典列表：返回 [{code, name, module, description}]
export function getPermissionList() {
  return request.get('/admin/rbac/permission/list')
}

// 为指定角色分配权限（全量替换）：body = {roleCode, permissionCodes: []}
export function assignRolePermissions(roleCode, permissionCodes) {
  return request.post('/admin/rbac/role/assign', { roleCode, permissionCodes })
}