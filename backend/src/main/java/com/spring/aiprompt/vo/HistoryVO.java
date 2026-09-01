package com.spring.aiprompt.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 使用记录出参：含 Prompt 标题与使用时间
 */
@Data
public class HistoryVO {

    /** 记录ID */
    private Long id;

    /** Prompt ID */
    private Long promptId;

    /** Prompt 标题 */
    private String promptTitle;

    /** 使用时间 */
    private LocalDateTime useTime;
}
