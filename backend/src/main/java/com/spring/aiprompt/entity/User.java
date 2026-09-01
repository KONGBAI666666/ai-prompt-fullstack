package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库表 sys_user
 * <p>
 * 这是系统的核心实体之一。每个用户注册后都会在 sys_user 表中产生一条记录。
 * <p>
 * 关键字段说明：
 * - password：存的是 BCrypt 密文（不是明文），即使数据库泄露，攻击者也无法反推密码
 * - role：用户主角色，取值 USER / ADMIN / SUPER_ADMIN，决定该用户能访问哪些接口
 * - status：1=正常，0=禁用；管理员禁用用户后，触发器自动写审计日志，且立即踢下线
 * <p>
 * 关键注解说明：
 * - @Data：Lombok 注解，自动生成 getter / setter / toString / equals / hashCode
 * - @TableName("sys_user")：MyBatis-Plus 注解，声明此类映射到 sys_user 表
 * - @TableId(type = IdType.AUTO)：主键策略，AUTO 表示数据库自增
 * - @TableField(fill = ...)：字段自动填充策略，配合 MyMetaObjectHandler 使用
 *   INSERT：插入时填充；INSERT_UPDATE：插入和更新都填充
 *   这就是为什么你从不需要在代码里 setCreateTime / setUpdateTime
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
