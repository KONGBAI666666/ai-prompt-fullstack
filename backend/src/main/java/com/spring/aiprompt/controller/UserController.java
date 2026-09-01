package com.spring.aiprompt.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.spring.aiprompt.common.Result;
import com.spring.aiprompt.dto.LoginDTO;
import com.spring.aiprompt.dto.RegisterDTO;
import com.spring.aiprompt.service.CaptchaService;
import com.spring.aiprompt.service.UserService;
import com.spring.aiprompt.vo.CaptchaVO;
import com.spring.aiprompt.vo.LoginVO;
import com.spring.aiprompt.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口 Controller —— 负责用户注册、登录、验证码、获取当前用户信息、退出登录
 * <p>
 * 路径前缀：/user（加上全局 context-path /api，完整路径是 /api/user/xxx）
 * <p>
 * 公开接口（不需要登录）：/user/captcha、/user/register、/user/login
 * 需要登录的接口：/user/info、/user/logout
 * （公开接口在 SaTokenConfig 的 saServletFilter 的 addExclude 白名单中配置）
 * <p>
 * @RestController：Spring 注解，表示这是一个 RESTful 控制器，
 *   它组合了 @Controller + @ResponseBody，所有方法返回值自动序列化为 JSON。
 * @RequestMapping("/user")：类级别路径前缀，类内所有方法路径都以 /user 开头。
 * @RequiredArgsConstructor：Lombok 注解，自动为 final 字段生成构造器（Spring 构造器注入）。
 * @Tag：Swagger 文档分组标签。
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    // —— 依赖注入 ——
    /** 用户业务：注册、登录、获取当前用户 */
    private final UserService userService;
    /** 验证码业务：生成图形验证码 */
    private final CaptchaService captchaService;

    /**
     * 获取图形验证码（公开接口）
     * <p>
     * 前端打开登录页时首先调这个接口拿一张验证码图。
     * 返回的 CaptchaVO 包含 id（UUID）和 image（Base64 图片字符串）。
     * 前端把 image 绑定到 img 标签的 src 属性就能显示验证码。
     *
     * @return CaptchaVO {id, image}
     */
    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.success(captchaService.generate());
    }

    /**
     * 用户注册（公开接口）
     * <p>
     * @Validated：触发 RegisterDTO 上的校验注解（@NotBlank、@Size、@Email）。
     * 如果校验不通过，Spring 会抛 MethodArgumentNotValidException，
     * 被 GlobalExceptionHandler 捕获后返回 400 + 第一条校验错误信息。
     *
     * @param dto 注册入参（用户名、密码、邮箱）
     * @return 成功（无 data）
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    /**
     * 用户登录（公开接口）
     * <p>
     * LoginDTO 四个字段都 @NotBlank：用户名、密码、captchaId、captchaCode。
     * 登录逻辑在 UserServiceImpl.login() 中实现（四道安全关）。
     *
     * @param dto 登录入参
     * @return LoginVO {token, user}
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Validated @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    /**
     * 获取当前登录用户信息（需要登录）
     * <p>
     * 能走到这里说明 Sa-Token 过滤器已校验过登录状态。
     * StpUtil.getLoginIdAsLong() 从 token 取出 userId，再查库返回脱敏后的用户信息。
     *
     * @return UserVO（不含 password）
     */
    @Operation(summary = "当前登录用户信息")
    @GetMapping("/info")
    public Result<UserVO> info() {
        return Result.success(userService.getCurrentUser());
    }

    /**
     * 退出登录（需要登录）
     * <p>
     * StpUtil.logout()：销毁当前会话，让 token 失效。
     * 之后该 token 再发请求会被 Sa-Token 过滤器拦截返回 401。
     */
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }
}
