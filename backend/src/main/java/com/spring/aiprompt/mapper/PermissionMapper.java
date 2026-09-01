package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限点表 Mapper —— RBAC 数据访问层
 * 继承 BaseMapper&lt;Permission&gt; 自动拥有增删改查能力。
 * 用于 PermissionServiceImpl 查询权限点字典，以及 StpInterfaceImpl 中过滤有效权限。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}