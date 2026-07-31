package com.spring.aiprompt.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息出参（不含密码）
 */
@Data
public class UserVO {

    /** 用户 id */
    private Long id;

    /** 用户名 */
    private String username;

    /** 邮箱 */
    private String email;

    /** 头像地址 */
    private String avatar;

    /** 角色：USER / ADMIN */
    private String role;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    /** 注册时间 */
    private LocalDateTime createTime;
}
