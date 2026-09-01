package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色表 Mapper —— RBAC 数据访问层
 * 继承 BaseMapper&lt;Role&gt; 自动拥有增删改查能力。
 * 用于 RoleServiceImpl 查询所有角色。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}