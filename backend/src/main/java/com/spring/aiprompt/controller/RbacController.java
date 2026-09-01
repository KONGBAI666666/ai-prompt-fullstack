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
 * 权限管理子系统接口（全部需要 ADMIN 角色）：
 * - 角色列表（含每个角色已绑定的权限编码）
 * - 权限点字典列表
 * - 角色-权限的分配
 *
 * 配合 Sa-Token 的 @SaCheckRole / @SaCheckPermission 形成"用户分组、授权、权限维护"完整能力。
 */
@Tag(name = "权限管理")
@RestController
@RequestMapping("/admin/rbac")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class RbacController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    @Operation(summary = "角色列表（含每个角色已绑定的权限）")
    @GetMapping("/role/list")
    public Result<List<Role>> roleList() {
        return Result.success(roleService.listAll());
    }

    @Operation(summary = "权限点字典列表")
    @GetMapping("/permission/list")
    public Result<List<Permission>> permissionList() {
        return Result.success(permissionService.listAll());
    }

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