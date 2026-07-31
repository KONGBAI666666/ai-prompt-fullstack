package com.spring.aiprompt.common;

import lombok.Data;

/**
 * 统一返回结果：{ "code": 200, "message": "success", "data": {} }
 */
@Data
public class Result<T> {

    /** 状态码：200 成功 / 400 业务错误 / 401 未登录 / 403 无权限 / 500 系统异常 */
    private Integer code;

    /** 提示消息 */
    private String message;

    /** 数据 */
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(400, message);
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
