package com.spring.aiprompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Prompt 新增/修改入参
 */
@Data
public class PromptDTO {

    /** Prompt 标题 */
    @NotBlank(message = "Prompt标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100")
    private String title;

    /** Prompt 正文 */
    @NotBlank(message = "Prompt内容不能为空")
    private String content;

    /** 描述（可选） */
    @Size(max = 500, message = "描述长度不能超过500")
    private String description;

    /** 分类 id */
    @NotNull(message = "分类不能为空")
    private Long categoryId;
}
