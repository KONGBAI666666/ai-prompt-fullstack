package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 使用记录实体，对应表 prompt_history
 */
@Data
@TableName("prompt_history")
public class PromptHistory {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 使用人用户 id */
    private Long userId;

    /** 使用的 Prompt id */
    private Long promptId;

    /** 使用时间（插入自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime useTime;
}
