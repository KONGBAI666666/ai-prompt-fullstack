package com.spring.aiprompt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spring.aiprompt.common.Result;
import com.spring.aiprompt.service.FavoriteService;
import com.spring.aiprompt.vo.PromptVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口：收藏 / 取消收藏 / 我的收藏列表
 */
@Tag(name = "收藏管理")
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** 收藏 Prompt */
    @Operation(summary = "收藏Prompt")
    @PostMapping("/{promptId}")
    public Result<Void> add(@PathVariable Long promptId) {
        favoriteService.add(promptId);
        return Result.success();
    }

    /** 取消收藏 */
    @Operation(summary = "取消收藏")
    @DeleteMapping("/{promptId}")
    public Result<Void> cancel(@PathVariable Long promptId) {
        favoriteService.cancel(promptId);
        return Result.success();
    }

    /** 我的收藏分页列表 */
    @Operation(summary = "我的收藏分页列表")
    @GetMapping("/list")
    public Result<Page<PromptVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(favoriteService.myFavorites(pageNum, pageSize));
    }
}
