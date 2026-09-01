package com.spring.aiprompt.exception;

import lombok.Getter;

/**
 * 业务异常 —— Service 层"可预期的错误"统一用这个
 * <p>
 * 使用方式：
 *   throw new BusinessException("用户名已存在");              // 默认 code=400
 *   throw new BusinessException(403, "只能修改自己的Prompt"); // 指定 code
 * <p>
 * 被谁捕获？
 * GlobalExceptionHandler 的 @ExceptionHandler(BusinessException.class) 会捕获它，
 * 用异常自带的 code 和 message 组装成 Result 给前端。
 * <p>
 * 为什么不用 RuntimeException？
 * 因为 RuntimeException 太宽泛，GlobalExceptionHandler 如果只捕获它，
 * 就无法区分"业务错误"和"系统 Bug"。用 BusinessException 专门表示业务错误，
 * 系统级别的未预期异常仍然走兜底的 Exception handler 返回 500。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码：400 业务错误（默认）/ 403 无权限 / 其他自定义 */
    private final Integer code;

    /**
     * 构造器 1：只传消息，默认 code=400
     */
    public BusinessException(String message) {
        this(400, message);
    }

    /**
     * 构造器 2：指定 code 和 message
     * @param code    HTTP 状态码语义（400/403 等）
     * @param message 错误提示信息（返回给前端展示给用户看）
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
