package com.spring.aiprompt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spring.aiprompt.entity.UserStatusLog;

/**
 * 用户状态变更审计日志服务
 * <p>
 * 只读服务：日志由数据库触发器写入，应用侧只提供查询。
 */
public interface UserStatusLogService {

    /**
     * 分页查询用户状态变更日志（按变更时间倒序，最新的在前）
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return MyBatis-Plus 分页对象
     */
    Page<UserStatusLog> pageLogs(long pageNum, long pageSize);
}
