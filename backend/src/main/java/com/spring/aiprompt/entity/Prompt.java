package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt（提示词）实体，对应数据库表 prompt
 * <p>
 * 这是系统的业务核心实体。用户发布的每一条 AI 提示词都是一条 prompt 记录。
 * <p>
 * 关键字段说明：
 * - content：提示词正文，即用户发送给 AI 的完整 Prompt 文本
 * - categoryId：外键，指向 category 表，表示这条提示词属于哪个分类
 * - userId：外键，指向 sys_user 表，表示谁创建的这条提示词（由后端从 token 取，不信任前端）
 * - viewCount：浏览次数，使用数据库原子操作递增（view_count = view_count + 1）
 * - favoriteCount：收藏次数，收藏时 +1，取消时 GREATEST(favorite_count - 1, 0) 防负数
 * - status：1=正常（预留状态字段，可用于软删除/审核下架功能）
 * <p>
 * 关联关系：
 * - 一个 Prompt 属于一个 Category（多对一）
 * - 一个 Prompt 属于一个 User（多对一）
 * - 一个 Prompt 可以被多个 User 收藏（一对多，通过 favorite 中间表）
 * - 一个 Prompt 可以被多个 User 使用（一对多，通过 prompt_history 中间表）
 */
@Data
@TableName("prompt")
public class Prompt {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Prompt 标题 */
    private String title;

    /** Prompt 正文（发送给 AI 的内容） */
    private String content;

    /** 描述 */
    private String description;

    /** 分类 id */
    private Long categoryId;

    /** 创建者用户 id */
    private Long userId;

    /** 浏览次数 */
    private Integer viewCount;

    /** 收藏次数 */
    private Integer favoriteCount;

    /** 状态：1 正常（第一版物理删除，本字段预留下架/审核） */
    private Integer status;

    /** 创建时间（插入自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入和更新自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
