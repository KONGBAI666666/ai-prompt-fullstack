package com.spring.aiprompt.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.spring.aiprompt.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：Service 抛异常 → 这里统一转为 Result JSON 返回
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：使用异常自带的 code / message */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：返回第一条校验消息（中文） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.error(400, message);
    }

    /** 未登录 / token 失效 */
    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        return Result.error(401, "请先登录");
    }

    /** 无角色 / 无权限 */
    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public Result<Void> handleNotRoleException(Exception e) {
        return Result.error(403, "无权限操作");
    }

    /** 兜底：未预期异常，打印堆栈但不返回给前端 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统异常");
    }
}
