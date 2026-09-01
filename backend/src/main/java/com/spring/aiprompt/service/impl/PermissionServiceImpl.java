package com.spring.aiprompt.service.impl;

import com.spring.aiprompt.entity.Permission;
import com.spring.aiprompt.mapper.PermissionMapper;
import com.spring.aiprompt.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限点服务实现
 * <p>
 * 职责：查询系统中定义的所有权限点（按模块、编码排序）。
 * 管理员在"权限管理"页面勾选权限时，右侧展示的权限点列表就是从这里来的。
 * <p>
 * 权限点是 RBAC 中"最小可授权动作"的概念，如：
 * - prompt:view（查看提示词）
 * - prompt:create（发布提示词）
 * - prompt:delete:own（删除自己的提示词）
 * - user:manage（管理用户）
 * - role:assign（分配权限）
 * <p>
 * 命名规范：模块:动作[:范围]，冒号分隔，如 prompt:create / prompt:delete:own
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;

    /**
     * 列出所有权限点（按模块、编码排序）
     * 前端"权限管理"页面用这个接口渲染权限点勾选矩阵
     *
     * @return 权限点列表
     */
    @Override
    public List<Permission> listAll() {
        // SELECT * FROM permission ORDER BY module ASC, code ASC
        // 按模块排序：让同模块的权限点相邻展示
        // 按编码排序：同模块内按编码字母序排列
        return permissionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Permission>()
                        .orderByAsc("module", "code"));
    }
}
