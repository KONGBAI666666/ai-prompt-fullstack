package com.spring.aiprompt.service.impl;

import com.spring.aiprompt.entity.Role;
import com.spring.aiprompt.exception.BusinessException;
import com.spring.aiprompt.mapper.RoleMapper;
import com.spring.aiprompt.mapper.RolePermissionMapper;
import com.spring.aiprompt.service.PermissionService;
import com.spring.aiprompt.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色服务实现 —— RBAC 子系统的核心
 * <p>
 * 职责：
 * 1. listAll()：查所有角色，每个角色带上已绑定的权限编码列表
 * 2. assignPermissions()：给角色分配权限（事务式全量替换）
 * <p>
 * RBAC 模型回顾：
 * - Role（角色）：权限的分组载体，如 USER / ADMIN / SUPER_ADMIN
 * - Permission（权限点）：系统中最小可授权的动作，如 prompt:create / user:manage
 * - RolePermission（角色-权限关联）：把角色和权限点多对多关联起来的中间表
 * - 用户不直接绑权限，而是绑角色；角色绑权限 → 用户通过角色间接获得权限
 * <p>
 * 注意：RoleServiceImpl 没有继承 ServiceImpl，因为 Role 的主键是字符串（code），
 * 不是自增 id，部分 ServiceImpl 方法不适用，所以直接用 RoleMapper 操作。
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionService permissionService;

    /**
     * 查询所有角色（含每个角色已绑定的权限编码列表）
     * <p>
     * N+1 优化：
     * 不用"查角色 → 循环查权限"，而是一次性查所有角色的权限绑定（listPermissionCodesOfRoles 批量查），
     * 避免角色数量多时产生 N+1 查询。
     *
     * @return 角色列表，每个 Role 的 permissionCodes 字段已填充
     */
    @Override
    public List<Role> listAll() {
        // 查全部角色：SELECT * FROM role
        List<Role> roles = roleMapper.selectList(null);
        // 收集所有角色编码
        List<String> codes = roles.stream().map(Role::getCode).toList();

        // 批量查所有角色的权限编码 → Map<roleCode, List<permissionCode>>
        Map<String, List<String>> map = listPermissionCodesOfRoles(codes);

        // 把权限编码列表填充到每个 Role 对象的 permissionCodes 字段
        // getOrDefault：如果该角色没有绑定任何权限，返回空列表（不会 NPE）
        for (Role r : roles) {
            r.setPermissionCodes(map.getOrDefault(r.getCode(), List.of()));
        }
        return roles;
    }

    /**
     * 查询单个角色已绑定的权限编码
     * 复用批量查询方法，传单个角色编码
     *
     * @param roleCode 角色编码
     * @return 权限编码列表
     */
    @Override
    public List<String> listPermissionCodesOfRole(String roleCode) {
        return listPermissionCodesOfRoles(List.of(roleCode)).getOrDefault(roleCode, List.of());
    }

    /**
     * 权限分配 —— RBAC 子系统最核心的方法
     * <p>
     * 策略：事务式全量替换（先删后写）
     * - 先删除该角色在 role_permission 表中的所有旧绑定
     * - 再批量插入传入的新权限绑定
     * - 整个过程在一个事务中：如果中间出异常（如插入失败），删除操作也会回滚，不会出现权限被清空的中间态
     * <p>
     * 安全规则：
     * 1. SUPER_ADMIN 角色的权限不可修改（系统内置，防止误操作把超级管理员权限清空后无法恢复）
     * 2. 传入的权限编码必须全部存在于 permission 表（防止写入脏数据）
     * <p>
     * 为什么是"全量替换"而不是"增量增删"？
     * - 界面上展示的是"这个角色最终应该有哪些权限"的完整勾选状态
     * - 保存时传上来的是"勾选的全部权限"，而不是"改了哪些"
     * - 全量替换最简单：不需要 diff 计算新增了哪些、删除了哪些，直接用传入的列表覆盖
     * - 配合事务，安全性有保障
     *
     * @param roleCode       目标角色编码
     * @param permissionCodes 该角色新的权限编码集合（null 或空列表表示清空所有权限）
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String roleCode, List<String> permissionCodes) {
        // 安全规则 1：SUPER_ADMIN 不可修改
        // 这是"最后一道保险"：万一管理员误操作把 ADMIN 角色权限清空，
        // 还能用 SUPER_ADMIN 登录系统恢复 ADMIN 的权限
        if ("SUPER_ADMIN".equals(roleCode)) {
            throw new BusinessException("SUPER_ADMIN 角色权限不可修改（系统内置）");
        }

        // null → 空列表，统一处理
        if (permissionCodes == null) {
            permissionCodes = List.of();
        }

        // 安全规则 2：校验权限编码合法性
        // 从 permission 字典表查出所有合法的权限编码
        List<String> allValid = permissionService.listAll().stream()
                .map(com.spring.aiprompt.entity.Permission::getCode).toList();
        // 逐一检查传入的权限编码是否都在合法列表中
        for (String pc : permissionCodes) {
            if (!allValid.contains(pc)) {
                throw new BusinessException("非法权限点：" + pc);
            }
        }

        // —— 以下两步在同一个事务中 ——

        // 第 1 步：删除该角色的所有旧权限绑定
        // DELETE FROM role_permission WHERE role_code = ?
        rolePermissionMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.spring.aiprompt.entity.RolePermission>()
                        .eq("role_code", roleCode));

        // 第 2 步：批量插入新权限绑定
        if (!permissionCodes.isEmpty()) {
            for (String pc : permissionCodes) {
                // INSERT INTO role_permission (role_code, permission_code) VALUES (?, ?)
                rolePermissionMapper.insert(
                        new com.spring.aiprompt.entity.RolePermission() {{
                            setRoleCode(roleCode);
                            setPermissionCode(pc);
                        }});
            }
        }
        // 事务提交后，StpInterfaceImpl 下次鉴权时查 role_permission 就能拿到最新权限
        // —— 这就是"权限修改即时生效"的原理：Sa-Token 每次校验都实时查库
    }

    /**
     * 批量查多个角色的权限编码（N+1 优化）
     * <p>
     * 一次查询取出所有角色的权限绑定，然后按 roleCode 分组。
     * 避免在循环里逐个角色查权限（N 个角色 = N+1 次查询）。
     *
     * @param roleCodes 角色编码列表
     * @return Map<角色编码, List<权限编码>>
     */
    private Map<String, List<String>> listPermissionCodesOfRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Map.of();
        }
        // SELECT * FROM role_permission WHERE role_code IN (?, ?, ...)
        List<com.spring.aiprompt.entity.RolePermission> rows = rolePermissionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.spring.aiprompt.entity.RolePermission>()
                        .in("role_code", roleCodes));

        // 按 roleCode 分组：Map<roleCode, List<permissionCode>>
        // Collectors.groupingBy：按 roleCode 分组
        // Collectors.mapping：每组只取 permissionCode
        return rows.stream().collect(Collectors.groupingBy(
                com.spring.aiprompt.entity.RolePermission::getRoleCode,
                Collectors.mapping(com.spring.aiprompt.entity.RolePermission::getPermissionCode,
                        Collectors.toList())));
    }
}
