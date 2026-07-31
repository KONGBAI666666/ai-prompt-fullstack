package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 sys_user
 */
@Data
@TableName("sys_user")
public class User {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（唯一） */
    private String username;

    /** 密码（BCrypt 密文） */
    private String password;

    /** 邮箱 */
    private String email;

    /** 头像地址 */
    private String avatar;

    /** 角色：USER / ADMIN */
    private String role;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    /** 创建时间（插入自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入和更新自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
