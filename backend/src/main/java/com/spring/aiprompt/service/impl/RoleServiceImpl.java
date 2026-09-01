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
 * 角色服务实现：
 * - 列表查询 / 单角色权限查询
 * - 权限分配（全量替换：先删后写，事务保证一致性）
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionService permissionService;

    @Override
    public List<Role> listAll() {
        List<Role> roles = roleMapper.selectList(null);
        List<String> codes = roles.stream().map(Role::getCode).toList();
        // 批量查所有角色的权限编码，避免 N+1
        Map<String, List<String>> map = listPermissionCodesOfRoles(codes);
        for (Role r : roles) {
            r.setPermissionCodes(map.getOrDefault(r.getCode(), List.of()));
        }
        return roles;
    }

    @Override
    public List<String> listPermissionCodesOfRole(String roleCode) {
        return listPermissionCodesOfRoles(List.of(roleCode)).getOrDefault(roleCode, List.of());
    }

    /**
     * 权限分配：把一个角色的权限集合替换为传入的权限集合
     * 业务规则：
     *   - 不允许修改 SUPER_ADMIN 的权限点（演示用固定配置），防止误操作把超级管理员权限清空
     *   - 传入的权限编码必须全部存在于 permission 表（避免脏数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String roleCode, List<String> permissionCodes) {
        if ("SUPER_ADMIN".equals(roleCode)) {
            throw new BusinessException("SUPER_ADMIN 角色权限不可修改（系统内置）");
        }
        if (permissionCodes == null) {
            permissionCodes = List.of();
        }
        // 校验权限编码合法性
        List<String> allValid = permissionService.listAll().stream()
                .map(com.spring.aiprompt.entity.Permission::getCode).toList();
        for (String pc : permissionCodes) {
            if (!allValid.contains(pc)) {
                throw new BusinessException("非法权限点：" + pc);
            }
        }
        // 先清空该角色的所有权限
        rolePermissionMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.spring.aiprompt.entity.RolePermission>()
                        .eq("role_code", roleCode));
        // 再批量写入
        if (!permissionCodes.isEmpty()) {
            for (String pc : permissionCodes) {
                rolePermissionMapper.insert(
                        new com.spring.aiprompt.entity.RolePermission() {{
                            setRoleCode(roleCode);
                            setPermissionCode(pc);
                        }});
            }
        }
    }

    /** 批量查：传入多个角色编码，返回 Map<roleCode, List<permissionCode>> */
    private Map<String, List<String>> listPermissionCodesOfRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Map.of();
        }
        List<com.spring.aiprompt.entity.RolePermission> rows = rolePermissionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.spring.aiprompt.entity.RolePermission>()
                        .in("role_code", roleCodes));
        return rows.stream().collect(Collectors.groupingBy(
                com.spring.aiprompt.entity.RolePermission::getRoleCode,
                Collectors.mapping(com.spring.aiprompt.entity.RolePermission::getPermissionCode,
                        Collectors.toList())));
    }
}