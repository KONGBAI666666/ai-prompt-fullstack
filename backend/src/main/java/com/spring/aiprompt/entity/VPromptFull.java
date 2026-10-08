package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v_prompt_full 视图只读实体
 * <p>
 * 对应数据库视图 v_prompt_full（prompt LEFT JOIN category LEFT JOIN sys_user），
 * 已在数据库层把分类名（category_name）和作者名（author_name）关联好，
 * 管理后台的内容列表与数据导出直接查该视图，省去业务层的批量回查。
 * <p>
 * ⚠️ 视图是只读对象：该实体只用于 SELECT 查询映射，禁止对它做 insert / update / delete，
 * 写操作仍走 Prompt 实体（prompt 主表）。
 * <p>
 * 视图定义见 sql/init.sql：
 * {@code CREATE VIEW v_prompt_full AS SELECT p.id, ..., c.name AS category_name, u.username AS author_name ...}
 */
@Data
@TableName("v_prompt_full")
public class VPromptFull {

    /** 提示词 id（视图继承 prompt 主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 描述 */
    private String description;

    /** 分类 id */
    private Long categoryId;

    /** 分类名称（视图已关联 category.name） */
    private String categoryName;

    /** 创建者用户 id */
    private Long userId;

    /** 作者用户名（视图已关联 sys_user.username） */
    private String authorName;

    /** 浏览次数 */
    private Integer viewCount;

    /** 收藏次数 */
    private Integer favoriteCount;

    /** 状态：1 正常，0 下架 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
