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
 * Sa-Token 权限数据源 —— RBAC 子系统与 Sa-Token 框架之间的桥梁
 * <p>
 * 作用：Sa-Token 框架本身只负责"认证"（你是谁，token 是否有效），
 * 但它不知道"你有什么角色、什么权限"。StpInterface 接口就是 Sa-Token 留给开发者的回调：
 * 当代码执行到 @SaCheckRole("ADMIN") 或 @SaCheckPermission("prompt:view") 时，
 * Sa-Token 会回调这个类的两个方法去数据库查：
 * - getRoleList(loginId) → 你有哪些角色
 * - getPermissionList(loginId) → 你有哪些权限点
 * <p>
 * 本实现的设计：
 * - 角色：从 sys_user.role 字段直接读（每个用户一个主角色，简单清晰）
 * - 权限：sys_user.role → role_permission → permission 两跳链查
 *   （用户主角色 → 该角色绑定的权限编码 → permission 字典表过滤确认有效）
 * <p>
 * 即时生效原理：
 * Sa-Token 每次鉴权（@SaCheckRole / @SaCheckPermission）都会回调这里实时查库。
 * 管理员在界面上改完角色权限后，下一次鉴权立即生效——不需要重启服务，不需要缓存失效逻辑。
 * <p>
 * @Component：让 Spring 管理这个 Bean，Sa-Token 通过 SPI 机制自动发现并加载。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    /**
     * 获取权限点列表（Sa-Token 回调）
     * <p>
     * 调用时机：当请求到达带 @SaCheckPermission("xxx") 注解的方法时，
     * Sa-Token 会调用此方法获取当前登录用户的权限列表，
     * 然后检查传入的 "xxx" 是否在该列表中。
     * <p>
     * 查询链路：
     * 1. 根据 loginId 查 User → 拿到 role 字段（如 "ADMIN"）
     * 2. 根据 role_code 查 role_permission → 拿到该角色的所有权限编码（如 ["prompt:view", "prompt:create", ...]）
     * 3. 根据权限编码批量查 permission 表 → 过滤掉已删除/不存在的权限点（只返回有效的）
     *
     * @param loginId   当前登录用户 id（Sa-Token 从 token 中解析出来的）
     * @param loginType 登录类型（多账号体系时区分，本项目只有一个账号体系，用不到）
     * @return 权限编码列表（如 ["prompt:view", "prompt:create", "user:manage"]）
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 第 1 跳：根据 userId 查用户，拿到 role 字段
        User user = userMapper.selectById(Long.valueOf(loginId.toString()));
        // 用户不存在或没有角色 → 返回空列表（无任何权限）
        if (user == null || user.getRole() == null || user.getRole().isBlank()) {
            return Collections.emptyList();
        }

        // 第 2 跳：查 role_permission 表，获取该角色绑定的所有权限编码
        // SELECT permission_code FROM role_permission WHERE role_code = ?
        List<String> codes = rolePermissionMapper.selectList(
                new QueryWrapper<RolePermission>().eq("role_code", user.getRole())
        ).stream().map(RolePermission::getPermissionCode).toList();

        // 该角色没有绑定任何权限 → 返回空列表
        if (codes.isEmpty()) {
            return Collections.emptyList();
        }

        // 第 3 跳：到 permission 字典表批量查，过滤掉无效的权限点
        // 这一步的作用：如果管理员在 permission 表里删除了某个权限点，
        // 但 role_permission 表还没同步清理（理论上不应该，但做防御性处理），
        // 这里就能把已删除的权限点过滤掉，不会返回脏数据
        List<Permission> valid = permissionMapper.selectBatchIds(codes);
        return valid.stream().map(Permission::getCode).collect(Collectors.toList());
    }

    /**
     * 获取角色列表（Sa-Token 回调）
     * <p>
     * 调用时机：当请求到达带 @SaCheckRole("ADMIN") 注解的方法时，
     * Sa-Token 会调用此方法获取当前登录用户的角色列表，
     * 然后检查传入的 "ADMIN" 是否在该列表中。
     * <p>
     * 本项目每个用户只有一个主角色（存在 sys_user.role 字段），
     * 所以直接返回单元素列表。
     *
     * @param loginId   当前登录用户 id
     * @param loginType 登录类型
     * @return 角色列表（如 ["ADMIN"]）
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 根据 userId 查用户
        User user = userMapper.selectById(Long.valueOf(loginId.toString()));
        // 用户不存在或没有角色 → 空列表
        if (user == null || user.getRole() == null || user.getRole().isBlank()) {
            return Collections.emptyList();
        }
        // 返回单元素列表：[user.getRole()]，如 ["ADMIN"] 或 ["USER"]
        return List.of(user.getRole());
    }
}
