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
 * 全局异常处理器 —— 所有异常的统一出口
 * <p>
 * @RestControllerAdvice：Spring 提供的注解，表示这是一个全局异常处理器，
 * 它会拦截所有 @RestController 抛出的异常，根据类型匹配对应的 @ExceptionHandler 方法。
 * <p>
 * 设计理念：Controller / Service 层不写 try-catch，
 * 遇到错误直接 throw，这里统一捕获并转为标准 Result 格式返回前端。
 * 前端只需看 code 就知道成败，message 有人类可读的提示。
 * <p>
 * 异常优先级（从高到低匹配）：
 * 1. BusinessException → 业务错误（如"用户名已存在"）
 * 2. MethodArgumentNotValidException → 参数校验失败（如"密码不能为空"）
 * 3. NotLoginException → 未登录/token 失效
 * 4. NotRoleException / NotPermissionException → 无权限
 * 5. Exception → 兜底，所有未预期异常统一返回 500
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常处理：使用异常自带的 code 和 message
     * 例：throw new BusinessException(403, "只能修改自己的Prompt")
     *     → 返回 {code: 403, message: "只能修改自己的Prompt", data: null}
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败处理
     * <p>
     * 当 @Validated 标注的 DTO 字段上的 @NotBlank / @Size / @Email 校验不通过时，
     * Spring 抛出 MethodArgumentNotValidException。
     * 这里取第一条错误消息返回（避免把所有错误一次性堆给用户）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        // getBindingResult().getFieldErrors() → 所有字段校验错误
        // get(0).getDefaultMessage() → 取第一条错误消息（DTO 里 @NotBlank(message="xxx") 的 xxx）
        String message = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.error(400, message);
    }

    /**
     * 未登录处理：token 不存在 / 已过期 / 已被踢下线
     * 返回 401，前端响应拦截器检测到 401 会自动跳转登录页
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        return Result.error(401, "请先登录");
    }

    /**
     * 无角色 / 无权限处理
     * <p>
     * @SaCheckRole("ADMIN") 不通过 → 抛 NotRoleException
     * @SaCheckPermission("xxx") 不通过 → 抛 NotPermissionException
     * 统一返回 403 "无权限操作"
     */
    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public Result<Void> handleNotRoleException(Exception e) {
        return Result.error(403, "无权限操作");
    }

    /**
     * 兜底：所有未预期的异常
     * <p>
     * 出现这种异常说明系统有 Bug，需要打日志记录堆栈供排查，
     * 但不能把堆栈信息返回给前端（安全：泄露技术细节，可被攻击者利用）。
     * 前端只看到统一的"系统异常"，具体原因看后端日志。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // log.error 打印完整堆栈，方便开发者排查
        log.error("系统异常", e);
        // 返回 500，不暴露堆栈给前端
        return Result.error(500, "系统异常");
    }
}
