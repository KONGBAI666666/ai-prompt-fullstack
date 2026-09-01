package com.spring.aiprompt.service.impl;

import cn.dev33.satoken.stp.StpInterface;
import com.spring.aiprompt.entity.User;
import com.spring.aiprompt.mapper.PermissionMapper;
import com.spring.aiprompt.mapper.RolePermissionMapper;
import com.spring.aiprompt.mapper.UserMapper;
import com.spring.aiprompt.entity.RolePermission;
import com.spring.aiprompt.entity.Permission;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sa-Token 角色/权限数据源：@SaCheckRole / @SaCheckPermission 校验时回调这里。
 *
 * - getRoleList：根据 sys_user.role 字段返回用户的主角色（兼容旧版）
 * - getPermissionList：根据 sys_user.role → role_permission → permission 链读取全部权限点
 *
 * 这样既保留了"角色"维度的简单判定，也支持"权限点"维度的细粒度校验。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.valueOf(loginId.toString()));
        if (user == null || user.getRole() == null || user.getRole().isBlank()) {
            return Collections.emptyList();
        }
        // 1) 该用户主角色绑定的所有权限编码
        List<String> codes = rolePermissionMapper.selectList(
                new QueryWrapper<RolePermission>().eq("role_code", user.getRole())
        ).stream().map(RolePermission::getPermissionCode).toList();
        if (codes.isEmpty()) {
            return Collections.emptyList();
        }
        // 2) 翻译成权限编码字符串（实际就是 permission.code，这里再做一次过滤确保有效）
        List<Permission> valid = permissionMapper.selectBatchIds(codes);
        return valid.stream().map(Permission::getCode).collect(Collectors.toList());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.valueOf(loginId.toString()));
        if (user == null || user.getRole() == null || user.getRole().isBlank()) {
            return Collections.emptyList();
        }
        return List.of(user.getRole());
    }
}