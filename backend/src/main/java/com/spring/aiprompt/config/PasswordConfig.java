package com.spring.aiprompt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密器配置
 * <p>
 * 注册 BCryptPasswordEncoder 为 Spring Bean，在 UserServiceImpl 中注入使用。
 * <p>
 * 为什么用 BCrypt？
 * 1. 自动加盐：每次加密会生成随机盐，同一密码两次加密的密文不同
 * 2. 不可逆：无法从密文反推明文
 * 3. 可验证：BCrypt 的密文里包含盐和成本因子，
 *    matches(明文, 密文) 会自动提取盐进行验证
 * 4. 成本因子可调：$2a$10$ 中的 10 是成本因子，
 *    增加 1 翻一倍计算量，可以对抗算力提升（暴力破解越来越慢）
 * <p>
 * 不用 MD5 的原因：MD5 已被证明不安全，彩虹表攻击可以秒破简单密码。
 */
@Configuration
public class PasswordConfig {

    /**
     * 创建 BCrypt 加密器 Bean
     * Spring 会在需要的地方自动注入（如 UserServiceImpl 的构造器注入）
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
