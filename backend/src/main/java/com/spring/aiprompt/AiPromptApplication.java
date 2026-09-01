package com.spring.aiprompt;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Prompt 管理系统 —— Spring Boot 启动类
 * <p>
 * 整个后端从这个类的 main 方法启动。
 * <p>
 * @SpringBootApplication：Spring Boot 核心注解，组合了三个注解：
 * - @SpringBootConfiguration：标记为配置类（等价于 @Configuration）
 * - @EnableAutoConfiguration：开启自动配置（Spring Boot 根据 pom.xml 依赖自动装配 Bean）
 * - @ComponentScan：组件扫描（扫描 com.spring.aiprompt 及子包下的所有 @Controller / @Service / @Component）
 * <p>
 * @MapperScan("com.spring.aiprompt.mapper")：告诉 MyBatis-Plus 去这个包下扫描 Mapper 接口，
 * 自动为每个 Mapper 生成代理实现类（所以 Mapper 接口不需要写 @Mapper 注解）。
 * <p>
 * @Slf4j：Lombok 注解，自动注入 log 对象，用于日志输出。
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.spring.aiprompt.mapper")
public class AiPromptApplication {

    /**
     * 程序入口
     * <p>
     * SpringApplication.run() 做了什么：
     * 1. 创建 Spring 容器（ApplicationContext）
     * 2. 扫描注解、创建 Bean
     * 3. 启动内嵌 Tomcat（默认 8080 端口）
     * 4. 注册所有 Controller 的路由映射
     * 5. 启动完成后，主线程阻塞，服务器持续运行
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiPromptApplication.class, args);
        log.info("AI Prompt 管理系统启动成功，接口文档：http://localhost:8080/api/swagger-ui.html");
    }
}
