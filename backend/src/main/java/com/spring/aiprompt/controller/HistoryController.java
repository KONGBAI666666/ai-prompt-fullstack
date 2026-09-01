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
 * 使用记录接口 Controller
 * <p>
 * 路径前缀：/history
 * 所有接口都需要登录。
 * <p>
 * 功能：
 * - POST /history/{promptId} → 记录一次使用（前端"复制 Prompt"时调用）
 * - GET  /history/list       → 我的使用记录分页列表
 */
@Tag(name = "使用记录")
@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {

    private final PromptHistoryService promptHistoryService;

    /**
     * 记录一次使用（需要登录）
     * <p>
     * 用户在详情页点"复制"按钮时，前端除了把内容复制到剪贴板，
     * 还调这个接口往 prompt_history 表插一条记录。
     * 这样个人中心的"使用记录"Tab 就能展示"你最近用过哪些提示词"。
     *
     * @param promptId 被使用的 Prompt id
     */
    @Operation(summary = "记录一次Prompt使用")
    @PostMapping("/{promptId}")
    public Result<Void> record(@PathVariable Long promptId) {
        promptHistoryService.record(promptId);
        return Result.success();
    }

    /**
     * 我的使用记录分页列表（需要登录）
     * <p>
     * 个人中心"使用记录"Tab 用这个接口。
     * 返回 HistoryVO 分页（含 Prompt 标题、使用时间）。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     */
    @Operation(summary = "我的使用记录分页列表")
    @GetMapping("/list")
    public Result<Page<HistoryVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                        @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(promptHistoryService.myHistory(pageNum, pageSize));
    }
}
