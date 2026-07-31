package com.spring.aiprompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 分类实体，对应表 category
 */
@Data
@TableName("category")
public class Category {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类描述 */
    private String description;

    /** 创建时间（插入自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
