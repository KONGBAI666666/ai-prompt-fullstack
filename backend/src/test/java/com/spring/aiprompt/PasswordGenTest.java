package com.spring.aiprompt;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生成 init.sql 需要的 BCrypt 密文（admin123 / 123456）
 */
public class PasswordGenTest {

    @Test
    public void genPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String adminHash = encoder.encode("admin123");
        String testHash = encoder.encode("123456");
        System.out.println("admin123 -> " + adminHash);
        System.out.println("123456   -> " + testHash);
        // 校验密文可以匹配回明文
        assertTrue(encoder.matches("admin123", adminHash));
        assertTrue(encoder.matches("123456", testHash));
    }
}
