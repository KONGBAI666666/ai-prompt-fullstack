package com.spring.aiprompt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Prompt 管理系统启动类
 */
@SpringBootApplication
@MapperScan("com.spring.aiprompt.mapper")
public class AiPromptApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPromptApplication.class, args);
        System.out.println("AI Prompt 管理系统启动成功，接口文档：http://localhost:8080/api/swagger-ui.html");
    }
}
