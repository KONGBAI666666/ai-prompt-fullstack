package com.spring.aiprompt.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 出参：在实体基础上补充分类名称、作者名称、当前用户是否已收藏
 */
@Data
public class PromptVO {

    /** Prompt id */
    private Long id;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 描述 */
    private String description;

    /** 分类 id */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 创建者用户 id */
    private Long userId;

    /** 创建者用户名 */
    private String username;

    /** 浏览次数 */
    private Integer viewCount;

    /** 收藏次数 */
    private Integer favoriteCount;

    /** 当前登录用户是否已收藏 */
    private Boolean favorited;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
