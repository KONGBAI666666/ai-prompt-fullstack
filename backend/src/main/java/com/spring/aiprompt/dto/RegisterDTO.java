package com.spring.aiprompt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册入参
 */
@Data
public class RegisterDTO {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;

    /** 密码（明文，注册时加密入库） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需在6~20之间")
    private String password;

    /** 邮箱（可选） */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;
}
