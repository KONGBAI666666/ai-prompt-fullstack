package com.spring.aiprompt.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 试运行入参
 */
@Data
public class AiRunDTO {

    /** 要试运行的提示词 id（从 prompt 表取正文，不信任前端传正文——防止绕过权限白嫖任意内容） */
    @NotNull(message = "promptId不能为空")
    private Long promptId;

    /** 用户的补充输入（可选：有些提示词不需要额外输入） */
    @Size(max = 2000, message = "补充输入不能超过2000字")
    private String input;
}
