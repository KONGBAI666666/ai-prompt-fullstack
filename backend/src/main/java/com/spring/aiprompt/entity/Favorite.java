package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏实体，对应表 favorite（user_id + prompt_id 联合唯一）
 */
@Data
@TableName("favorite")
public class Favorite {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收藏人用户 id */
    private Long userId;

    /** 被收藏的 Prompt id */
    private Long promptId;

    /** 收藏时间（插入自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
