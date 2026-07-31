package com.spring.aiprompt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spring.aiprompt.common.Result;
import com.spring.aiprompt.service.PromptHistoryService;
import com.spring.aiprompt.vo.HistoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 使用记录接口：记录使用 / 我的使用记录列表
 */
@Tag(name = "使用记录")
@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {

    private final PromptHistoryService promptHistoryService;

    /** 记录一次使用（前端"复制Prompt"时调用） */
    @Operation(summary = "记录一次Prompt使用")
    @PostMapping("/{promptId}")
    public Result<Void> record(@PathVariable Long promptId) {
        promptHistoryService.record(promptId);
        return Result.success();
    }

    /** 我的使用记录分页列表 */
    @Operation(summary = "我的使用记录分页列表")
    @GetMapping("/list")
    public Result<Page<HistoryVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                        @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(promptHistoryService.myHistory(pageNum, pageSize));
    }
}
