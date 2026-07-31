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
 * Sa-Token 配置：
 * 1. SaServletFilter 全局过滤器——白名单外一律要求登录，未登录返回 JSON（不返回 HTML 错误页）
 * 2. SaInterceptor 注解拦截器——让 @SaCheckRole("ADMIN") 等注解生效
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /** 注解鉴权拦截器（抛出的 NotRoleException 由 GlobalExceptionHandler 转 403） */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }

    /** 全局登录过滤器（路径不含 context-path /api，starter 自动剔除） */
    @Bean
    public SaServletFilter saServletFilter() {
        return new SaServletFilter()
                .addInclude("/**")
                .addExclude(
                        "/user/register",
                        "/user/login",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/favicon.ico",
                        // Spring Boot 错误转发路径：404 等场景不应被登录校验掩盖
                        "/error")
                .setAuth(obj -> StpUtil.checkLogin())
                .setError(e -> {
                    // 过滤器在 DispatcherServlet 之前，GlobalExceptionHandler 捕获不到，这里直接写 JSON
                    SaHolder.getResponse().setHeader("Content-Type", "application/json;charset=utf-8");
                    return "{\"code\":401,\"message\":\"请先登录\",\"data\":null}";
                });
    }
}
