package com.spring.aiprompt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置（CORS）—— 解决前后端端口不同导致的跨域问题
 * <p>
 * 为什么要配 CORS？
 * - 前端运行在 5173 端口，后端运行在 8080 端口
 * - 浏览器安全策略：不同端口的请求属于"跨域"，默认会被拦截
 * - 后端需要通过 CORS 告诉浏览器"5173 来的请求我允许"
 * <p>
 * 关键设计：
 * 1. allowedOrigins 从配置文件读取（不硬编码），部署到不同环境改配置即可
 * 2. allowCredentials=true（允许携带 Cookie/Authorization 头）
 * 3. 优先级 HIGHEST_PRECEDENCE：必须在 Sa-Token 过滤器之前执行
 *    —— 浏览器跨域时会先发 OPTIONS 预检请求，这个请求不带 token，
 *    如果 CORS 过滤器在 Sa-Token 之后，预检请求会被 Sa-Token 当作"未登录"拦掉
 */
@Configuration
public class CorsConfig {

    /** 允许跨域的来源，从配置文件读取（逗号分隔），默认只放开本地前端 */
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    /**
     * 注册 CORS 过滤器
     * <p>
     * FilterRegistrationBean 是 Spring Boot 提供的过滤器注册器，
     * 可以配置过滤器的执行顺序（setOrder）。
     *
     * @return CORS 过滤器注册 Bean
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        // setAllowedOriginPatterns（而不是 setAllowedOrigins）：
        // 当 allowCredentials=true 时，不能用 "*" 作 allowedOrigins（浏览器规范限制），
        // 用 OriginPatterns 可以指定具体的来源列表
        config.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        // 允许的 HTTP 方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许的请求头（"*" 表示所有）
        config.setAllowedHeaders(List.of("*"));
        // 允许携带凭证（Cookie / Authorization 头）
        config.setAllowCredentials(true);

        // 把 CORS 配置注册到所有路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        // 关键：CORS 过滤器必须在 Sa-Token 过滤器之前执行
        // HIGHEST_PRECEDENCE 是 Spring 定义的最高优先级常量
        // 否则 OPTIONS 预检请求（不带 token）会被 Sa-Token 过滤器拦截返回 401
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
