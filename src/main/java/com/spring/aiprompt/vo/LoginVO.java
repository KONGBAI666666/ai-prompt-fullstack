package com.spring.aiprompt.vo;

import lombok.Data;

/**
 * 登录成功出参：token + 用户信息
 */
@Data
public class LoginVO {

    /** Sa-Token 生成的 token */
    private String token;

    /** 登录用户信息 */
    private UserVO user;
}
