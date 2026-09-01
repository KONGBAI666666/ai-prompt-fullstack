package com.spring.aiprompt.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 图形验证码出参：id 用于登录时回传校验，image 为 Base64 PNG（含 data: 前缀，前端可直接绑定到 img src）
 */
@Data
@AllArgsConstructor
public class CaptchaVO {

    /** 验证码唯一标识（一次性，登录校验后即失效） */
    private String id;

    /** Base64 编码的 PNG 图片 */
    private String image;
}
