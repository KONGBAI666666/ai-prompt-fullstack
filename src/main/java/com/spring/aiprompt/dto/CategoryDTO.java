package com.spring.aiprompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新增入参
 */
@Data
public class CategoryDTO {

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50")
    private String name;

    /** 分类描述（可选） */
    @Size(max = 200, message = "分类描述长度不能超过200")
    private String description;
}
