package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 权限点实体，对应表 permission
 * 一个权限点代表系统中可授权的最小动作单元（按钮级）
 */
@Data
@TableName("permission")
public class Permission {

    /** 权限点编码（主键），如 prompt:create / user:manage */
    @TableId(type = IdType.INPUT)
    private String code;

    /** 权限点名称 */
    private String name;

    /** 所属模块 */
    private String module;

    /** 权限点描述 */
    private String description;
}