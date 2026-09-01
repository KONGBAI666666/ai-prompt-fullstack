package com.spring.aiprompt;

import com.spring.aiprompt.entity.Category;
import com.spring.aiprompt.entity.User;
import com.spring.aiprompt.mapper.CategoryMapper;
import com.spring.aiprompt.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 启动上下文测试：验证数据源连接与初始数据
 */
@SpringBootTest
class AiPromptApplicationTests {

    @Resource
    private UserMapper userMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void initDataLoaded() {
        // 初始用户：admin / test
        List<User> users = userMapper.selectList(null);
        assertTrue(users.size() >= 2, "应至少存在 admin 和 test 两个初始用户");

        // 初始分类 10 个（管理员可新增分类，故用 >= 断言）
        List<Category> categories = categoryMapper.selectList(null);
        assertTrue(categories.size() >= 10, "应至少存在 10 条默认分类");
    }
}
