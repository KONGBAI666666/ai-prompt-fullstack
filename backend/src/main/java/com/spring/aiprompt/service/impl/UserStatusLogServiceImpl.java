package com.spring.aiprompt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spring.aiprompt.entity.UserStatusLog;
import com.spring.aiprompt.mapper.UserStatusLogMapper;
import com.spring.aiprompt.service.UserStatusLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户状态变更审计日志实现
 * <p>
 * 这个类刻意做得很薄 —— 因为写日志的责任已经交给数据库触发器了，
 * 应用层只负责"把触发器积累的记录读出来给管理员看"。
 * <p>
 * 为什么读日志不额外加权限点：审计日志是用户管理的从属视图，
 * 复用 user:list 权限即可，避免为了一个新接口往 permission 表插权限点
 * （新增权限点还要同步补 role_permission 绑定，否则管理员访问会 403）。
 */
@Service
@RequiredArgsConstructor
public class UserStatusLogServiceImpl implements UserStatusLogService {

    private final UserStatusLogMapper userStatusLogMapper;

    /**
     * 分页查询审计日志
     * <p>
     * 按 change_time 倒序：管理员最关心"刚刚发生了什么"。
     * 分页由 MyBatis-Plus 的 PaginationInnerInterceptor 自动改写 SQL 实现。
     */
    @Override
    public Page<UserStatusLog> pageLogs(long pageNum, long pageSize) {
        return userStatusLogMapper.selectPage(
                new Page<>(pageNum, pageSize),
                Wrappers.<UserStatusLog>lambdaQuery()
                        .orderByDesc(UserStatusLog::getChangeTime)
                        .orderByDesc(UserStatusLog::getId));
    }
}
