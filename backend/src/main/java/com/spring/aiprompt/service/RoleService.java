package com.spring.aiprompt.service;

import com.spring.aiprompt.entity.Role;

import java.util.List;

/**
 * 角色服务：列表查询、查看角色绑定的权限编码
 */
public interface RoleService {

    /** 所有角色（含每个角色已绑定的权限编码列表） */
    List<Role> listAll();

    /** 查询单个角色已绑定的权限编码 */
    List<String> listPermissionCodesOfRole(String roleCode);

    /**
     * 权限分配：把指定角色的权限集合全量替换为传入的权限集合（事务）
     * @param roleCode       目标角色编码
     * @param permissionCodes 该角色新的权限编码集合（null/empty 表示清空）
     */
    void assignPermissions(String roleCode, List<String> permissionCodes);
}