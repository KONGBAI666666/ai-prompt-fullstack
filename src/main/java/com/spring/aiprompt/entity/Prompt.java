package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 实体，对应表 prompt
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
