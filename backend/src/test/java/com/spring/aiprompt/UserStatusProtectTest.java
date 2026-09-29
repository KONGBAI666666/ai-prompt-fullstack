package com.spring.aiprompt;

import com.spring.aiprompt.entity.User;
import com.spring.aiprompt.exception.BusinessException;
import com.spring.aiprompt.mapper.UserMapper;
import com.spring.aiprompt.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户启停接口的角色保护的回归测试
 * <p>
 * 为什么要用测试而不是点界面验证：
 * - 库里只有一个 ADMIN 账号（admin）且没有 SUPER_ADMIN 账号，
 *   "禁用超管应被拒绝"这类用例没法在界面上手动复现（总不能把唯一的管理员禁掉试试）。
 * <p>
 * @Transactional 加在类上：每个测试方法跑完后 Spring Test 自动回滚，
 * 测试里造的用户不会留在数据库里。Service 方法内部的 @Transactional 会加入这个外层事务，
 * 所以整条链路共用一个可回滚的事务。
 */
@SpringBootTest
@Transactional
class  UserStatusProtectTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 造一个指定角色的测试用户
     * password 填占位值即可：本测试不涉及密码校验（登录流程另有验证码，不在测试范围）
     */
    private User insertUser(String username, String role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholde");
        u.setRole(role);
        u.setStatus(1);
        userMapper.insert(u);
        return u;
    }

    /** SUPER_ADMIN 是权限兜底的最终治理者，任何情况都不能被禁用 */
    @Test
    void superAdminCannotBeDisabled() {
        User sa = insertUser("__test_super_admin", "SUPER_ADMIN");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUserStatus(sa.getId(), 0));
        assertTrue(ex.getMessage().contains("超级管理员"),
                "提示语应说明超管不可禁用，实际为：" + ex.getMessage());

        assertEquals(1, userMapper.selectById(sa.getId()).getStatus(),
                "禁用被拒绝后，数据库里的状态不应发生任何变化");
    }

    /** ADMIN 之间不允许互相禁用，避免管理员被清空 */
    @Test
    void adminCannotBeDisabled() {
        User admin = insertUser("__test_admin", "ADMIN");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUserStatus(admin.getId(), 0));
        assertTrue(ex.getMessage().contains("管理员"),
                "提示语应说明管理员不可禁用，实际为：" + ex.getMessage());

        assertEquals(1, userMapper.selectById(admin.getId()).getStatus());
    }

    /** 普通用户正常禁用 / 恢复，确认保护逻辑没有误伤 */
    @Test
    void normalUserCanBeDisabledAndReEnabled() {
        User u = insertUser("__test_normal_user", "USER");

        userService.updateUserStatus(u.getId(), 0);
        assertEquals(0, userMapper.selectById(u.getId()).getStatus(), "应被禁用");

        userService.updateUserStatus(u.getId(), 1);
        assertEquals(1, userMapper.selectById(u.getId()).getStatus(), "应被恢复");
    }

    /** status 只接受 0 / 1 */
    @Test
    void invalidStatusRejected() {
        User u = insertUser("__test_bad_status_user", "USER");

        assertThrows(BusinessException.class, () -> userService.updateUserStatus(u.getId(), 9));
        assertThrows(BusinessException.class, () -> userService.updateUserStatus(u.getId(), null));
    }

    /** 不存在的用户应报"用户不存在"，而不是静默成功 */
    @Test
    void unknownUserRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUserStatus(-1L, 0));
        assertTrue(ex.getMessage().contains("用户不存在"), "实际为：" + ex.getMessage());
    }
}
