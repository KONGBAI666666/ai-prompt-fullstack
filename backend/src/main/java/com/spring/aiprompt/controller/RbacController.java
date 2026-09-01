package com.spring.aiprompt.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.spring.aiprompt.common.Result;
import com.spring.aiprompt.entity.Permission;
import com.spring.aiprompt.entity.Role;
import com.spring.aiprompt.service.PermissionService;
import com.spring.aiprompt.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 权限管理子系统接口 Controller（RBAC）
 * <p>
 * 路径前缀：/admin/rbac
 * 整个类加 @SaCheckRole("ADMIN")：只有管理员能访问 RBAC 管理接口。
 * <p>
 * 三个接口：
 * - GET  /admin/rbac/role/list       → 角色列表（每个角色含已绑定的权限编码）
 * - GET  /admin/rbac/permission/list → 权限点字典列表
 * - POST /admin/rbac/role/assign     → 为指定角色分配权限（全量替换）
 * <p>
 * 配合 Sa-Token 的 @SaCheckRole / @SaCheckPermission 形成
 * "角色定义 → 权限分配 → 权限校验" 的完整 RBAC 闭环。
 */
@Tag(name = "权限管理")
@RestController
@RequestMapping("/admin/rbac")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class RbacController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    /**
     * 角色列表（含每个角色已绑定的权限）
     * <p>
     * 前端"权限管理"Tab 左侧角色列表用这个接口。
     * 返回的每个 Role 对象的 permissionCodes 字段已填充该角色的权限编码列表。
     */
    @Operation(summary = "角色列表（含每个角色已绑定的权限）")
    @GetMapping("/role/list")
    public Result<List<Role>> roleList() {
        return Result.success(roleService.listAll());
    }

    /**
     * 权限点字典列表
     * <p>
     * 前端"权限管理"Tab 右侧权限点勾选矩阵用这个接口。
     * 返回系统定义的全部权限点，按模块分组展示。
     */
    @Operation(summary = "权限点字典列表")
    @GetMapping("/permission/list")
    public Result<List<Permission>> permissionList() {
        return Result.success(permissionService.listAll());
    }

    /**
     * 为指定角色分配权限（全量替换）
     * <p>
     * 管理员在界面上勾选权限点后点"保存分配"调用此接口。
     * <p>
     * 请求体格式：{"roleCode": "USER", "permissionCodes": ["prompt:view", "prompt:create", ...]}
     * <p>
     * Service 层在事务中执行：先删旧绑定 → 再批量写新绑定。
     * SUPER_ADMIN 角色不可修改（系统内置保护）。
     * <p>
     * 分配后即时生效：因为 StpInterfaceImpl 每次鉴权都实时查库。
     *
     * @param body 请求体 {roleCode, permissionCodes}
     */
    @Operation(summary = "为指定角色分配权限（全量替换）")
    @PostMapping("/role/assign")
    public Result<Void> assignRole(@RequestBody Map<String, Object> body) {
        String roleCode = (String) body.get("roleCode");
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) body.get("permissionCodes");
        roleService.assignPermissions(roleCode, codes);
        return Result.success();
    }
}
