package com.spring.aiprompt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）接口文档配置
 * <p>
 * 启动后访问 http://localhost:8080/api/swagger-ui.html 可以看到所有接口的在线文档，
 * 还能在页面上直接测试接口（先点 Authorize 填入登录返回的 token，就能测试需要登录的接口）。
 * <p>
 * 本配置做了两件事：
 * 1. 定义文档信息（标题、描述、版本）
 * 2. 定义全局 Authorization 请求头（让 Swagger UI 右上角出现 Authorize 按钮，
 *    填入 token 后，所有测试请求自动带上 Authorization: Bearer <token> 头）
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // 文档基本信息
                .info(new Info()
                        .title("AI Prompt 管理系统 API")
                        .description("Prompt 创建/分类/搜索/收藏/使用记录管理，登录后在 Authorize 中填入 login 返回的 token")
                        .version("v1.0"))
                // 定义安全方案：API Key 方式，通过 HTTP Header 传 token
                .components(new Components().addSecuritySchemes("Authorization",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")))
                // 全局应用：所有接口都需要 Authorization 头
                .addSecurityItem(new SecurityRequirement().addList("Authorization"));
    }
}
