package com.spring.aiprompt.exception;

import lombok.Getter;

/**
 * 业务异常：Service 层直接抛出，由 GlobalExceptionHandler 统一转为 Result
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码，默认 400 */
    private final Integer code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
