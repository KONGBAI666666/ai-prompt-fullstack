package com.spring.aiprompt.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spring.aiprompt.common.Result;
import com.spring.aiprompt.dto.PromptDTO;
import com.spring.aiprompt.service.PromptService;
import com.spring.aiprompt.vo.PromptVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 接口：创建 / 分页搜索 / 我的列表 / 详情 / 修改 / 删除
 */
@Tag(name = "Prompt管理")
@RestController
@RequestMapping("/prompt")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    /** 创建 Prompt */
    @Operation(summary = "创建Prompt")
    @PostMapping
    public Result<Void> create(@Validated @RequestBody PromptDTO dto) {
        promptService.create(dto);
        return Result.success();
    }

    /** 分页搜索列表：keyword 匹配标题或描述，categoryId 可选 */
    @Operation(summary = "Prompt分页搜索列表")
    @GetMapping("/list")
    public Result<Page<PromptVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Long categoryId) {
        return Result.success(promptService.pageList(pageNum, pageSize, keyword, categoryId, null));
    }

    /** 我的 Prompt 分页列表 */
    @Operation(summary = "我的Prompt分页列表")
    @GetMapping("/my")
    public Result<Page<PromptVO>> my(@RequestParam(defaultValue = "1") long pageNum,
                                     @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(promptService.pageList(pageNum, pageSize, null, null, StpUtil.getLoginIdAsLong()));
    }

    /** 详情（浏览次数 +1） */
    @Operation(summary = "Prompt详情（浏览次数+1）")
    @GetMapping("/{id}")
    public Result<PromptVO> detail(@PathVariable Long id) {
        return Result.success(promptService.detail(id));
    }

    /** 编辑回填（不增加浏览次数） */
    @Operation(summary = "编辑回填详情（不增加浏览次数）")
    @GetMapping("/{id}/edit")
    public Result<PromptVO> detailForEdit(@PathVariable Long id) {
        return Result.success(promptService.getForEdit(id));
    }

    /** 修改（仅本人） */
    @Operation(summary = "修改自己的Prompt")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Validated @RequestBody PromptDTO dto) {
        promptService.updatePrompt(id, dto);
        return Result.success();
    }

    /** 删除（本人或管理员，级联清理收藏/使用记录） */
    @Operation(summary = "删除Prompt（本人或管理员）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptService.deletePrompt(id);
        return Result.success();
    }
}
