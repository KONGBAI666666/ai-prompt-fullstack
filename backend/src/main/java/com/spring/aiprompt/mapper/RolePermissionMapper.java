package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联表 Mapper —— RBAC 数据访问层
 * 继承 BaseMapper&lt;RolePermission&gt; 自动拥有增删改查能力。
 * 用于 RoleServiceImpl 权限分配（先删后写）和权限查询，
 * 以及 StpInterfaceImpl 中根据角色查权限编码列表。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}