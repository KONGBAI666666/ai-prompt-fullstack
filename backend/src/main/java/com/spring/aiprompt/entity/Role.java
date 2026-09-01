package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色实体，对应表 role
 * 角色是 RBAC 中的"用户分组"载体，由角色→权限的关联实现授权
 */
@Data
@TableName("role")
public class Role {

    /** 角色编码（主键），如 USER / ADMIN / SUPER_ADMIN */
    @TableId(type = IdType.INPUT)
    private String code;

    /** 角色名称 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 创建时间 */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 该角色绑定的权限编码列表（联表查询时填充，业务表不含此字段） */
    @TableField(exist = false)
    private List<String> permissionCodes;
}