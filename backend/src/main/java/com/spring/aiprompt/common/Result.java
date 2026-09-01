package com.spring.aiprompt.common;

import lombok.Data;

/**
 * 统一返回结果信封 —— 所有接口的出口格式统一为 {code, message, data}
 * <p>
 * 前端只需检查 code 字段就能判断请求成败：
 * - 200：成功，data 里是业务数据
 * - 400：业务错误（如"用户名已存在"），message 里是错误提示
 * - 401：未登录或 token 失效，前端应跳转登录页
 * - 403：无权限（如普通用户访问管理员接口）
 * - 500：系统异常（后端 Bug，前端展示"系统异常"）
 * <p>
 * 泛型 &lt;T&gt;：data 的类型由调用方指定，编译时类型安全。
 * 如 Result&lt;LoginVO&gt; 表示 data 是 LoginVO 类型。
 * <p>
 * 使用方式（Controller 层）：
 *   return Result.success(data);           // 成功，带数据
 *   return Result.success();               // 成功，无数据
 *   return Result.error("用户名已存在");    // 业务错误，code 默认 400
 *   return Result.error(403, "无权限");     // 指定 code
 * <p>
 * 前端 Axios 响应拦截器会先检查 code：
 * - code=200 → 返回 data 给页面
 * - code≠200 → 弹错误提示 + reject
 * - code=401 → 清登录状态 + 跳登录页
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
