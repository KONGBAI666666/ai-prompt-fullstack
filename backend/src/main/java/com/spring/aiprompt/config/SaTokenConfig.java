package com.spring.aiprompt.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置 —— 系统安全防线的地基
 * <p>
 * 配置两个组件：
 * 1. SaServletFilter（全局过滤器）—— 第一层防线：白名单外一律要求登录
 * 2. SaInterceptor（注解拦截器）—— 第二层防线：让 @SaCheckRole / @SaCheckPermission 注解生效
 * <p>
 * 两层防线的区别：
 * - 过滤器在 DispatcherServlet 之前执行，更早拦截请求
 * - 拦截器在 DispatcherServlet 之后、Controller 之前执行，可以读到路由信息和注解
 * - 过滤器管"有没有登录"，拦截器管"有没有角色/权限"
 * <p>
 * 白名单（不需要登录就能访问的接口）：
 * - /user/register：注册（还没登录才能注册）
 * - /user/login：登录（还没登录才能登录）
 * - /user/captcha：验证码（登录前要先拿到验证码）
 * - /swagger-ui.html 等：接口文档（开发期调试用）
 * - /error：Spring Boot 错误转发路径
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册注解鉴权拦截器（第二层防线）
     * <p>
     * 让 @SaCheckRole("ADMIN") / @SaCheckPermission("prompt:view") 等注解生效。
     * 拦截所有路径（/**），当方法上有这些注解时，
     * SaInterceptor 会回调 StpInterfaceImpl 去数据库查角色/权限，不匹配则抛异常。
     * 抛出的 NotRoleException / NotPermissionException 被 GlobalExceptionHandler 转为 403。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }

    /**
     * 全局登录过滤器（第一层防线）
     * <p>
     * 在 DispatcherServlet 之前执行，拦截所有请求，
     * 白名单内的放行，白名单外的要求 StpUtil.checkLogin()（即必须已登录）。
     * <p>
     * 为什么不用 GlobalExceptionHandler 处理 401？
     * 因为过滤器在 DispatcherServlet 之前执行，GlobalExceptionHandler
     * （@RestControllerAdvice）只能捕获 Controller 层的异常，捕获不到过滤器层的。
     * 所以这里用 setError 直接写 JSON 响应。
     *
     * @return Sa-Token Servlet 过滤器
     */
    @Bean
    public SaServletFilter saServletFilter() {
        return new SaServletFilter()
                // 拦截所有路径
                .addInclude("/**")
                // 白名单：这些路径不需要登录就能访问
                .addExclude(
                        "/user/register",   // 注册
                        "/user/login",      // 登录
                        "/user/captcha",    // 获取验证码
                        "/swagger-ui.html", // Swagger 文档
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/favicon.ico",
                        "/error"             // Spring Boot 错误转发路径
                )
                // 鉴权逻辑：检查是否已登录
                // StpUtil.checkLogin()：如果未登录会抛 NotLoginException
                .setAuth(obj -> StpUtil.checkLogin())
                // 异常处理：过滤器层抛的异常 GlobalExceptionHandler 捕不到，这里直接写 JSON
                .setError(e -> {
                    SaHolder.getResponse().setHeader("Content-Type", "application/json;charset=utf-8");
                    return "{\"code\":401,\"message\":\"请先登录\",\"data\":null}";
                });
    }
}
