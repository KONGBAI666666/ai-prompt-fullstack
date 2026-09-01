package com.spring.aiprompt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录入参
 */
@Data
public class LoginDTO {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 图形验证码标识（由 /user/captcha 接口下发） */
    @NotBlank(message = "验证码不能为空")
    private String captchaId;

    /** 用户输入的图形验证码 */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
