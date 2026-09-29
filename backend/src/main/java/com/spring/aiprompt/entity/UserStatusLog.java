package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户状态变更审计日志实体，对应表 user_status_log
 * <p>
 * 注意：这张表的数据<b>不由 Java 代码写入</b>，而是由数据库触发器
 * trg_user_status_change 在 sys_user.status 发生变化时自动插入（见 init.sql）。
 * 应用侧只负责读取展示 —— 管理后台「操作日志」Tab。
 * <p>
 * 这样设计的意义：审计日志留在数据库层，
 * 即使有人绕过应用直接 UPDATE 数据库，日志照样会记下来。
 */
@Data
@TableName("user_status_log")
public class UserStatusLog {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被操作的用户 id */
    private Long userId;

    /** 用户名快照：保留变更当时的用户名，避免用户改名后历史日志失真 */
    private String username;

    /** 变更前状态：1 正常 0 禁用 */
    private Integer oldStatus;

    /** 变更后状态：1 正常 0 禁用 */
    private Integer newStatus;

    /** 变更时间（由触发器的 NOW() 写入） */
    private LocalDateTime changeTime;
}
