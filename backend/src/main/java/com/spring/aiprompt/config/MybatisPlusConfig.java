package com.spring.aiprompt.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置 —— 注册分页插件
 * <p>
 * 为什么要配这个？
 * Service 层调用 page(new Page<>(1, 10), wrapper) 时，
 * MyBatis-Plus 的分页插件会自动在 SQL 后面拼接 LIMIT 0, 10，
 * 并额外执行一条 SELECT COUNT(*) 获取总数。
 * 如果不注册这个插件，分页查询的 total 恒为 0，LIMIT 也不生效。
 * <p>
 * DbType.MYSQL：告诉插件当前数据库是 MySQL，生成的分页 SQL 用 LIMIT 语法。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 拦截器（含分页插件）
     *
     * @return 拦截器 Bean
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建拦截器（拦截器可以叠加多个）
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页内置拦截器，指定数据库类型为 MySQL
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
