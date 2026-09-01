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
 * 收藏接口 Controller
 * <p>
 * 路径前缀：/favorite
 * 所有接口都需要登录。
 * <p>
 * RESTful 设计：
 * - POST   /favorite/{promptId} → 收藏
 * - DELETE /favorite/{promptId} → 取消收藏
 * - GET    /favorite/list       → 我的收藏分页列表
 */
@Tag(name = "收藏管理")
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 收藏 Prompt（需要登录）
     * <p>
     * 用户在首页或详情页点"收藏"按钮时调用。
     * Service 层会做查重 + 唯一索引兜底 + 原子计数。
     *
     * @param promptId 被收藏的 Prompt id
     */
    @Operation(summary = "收藏Prompt")
    @PostMapping("/{promptId}")
    public Result<Void> add(@PathVariable Long promptId) {
        favoriteService.add(promptId);
        return Result.success();
    }

    /**
     * 取消收藏（需要登录）
     * <p>
     * 用户点"取消收藏"按钮时调用。
     * Service 层会删除收藏记录 + 原子减计数（GREATEST 防负数）。
     *
     * @param promptId 被取消收藏的 Prompt id
     */
    @Operation(summary = "取消收藏")
    @DeleteMapping("/{promptId}")
    public Result<Void> cancel(@PathVariable Long promptId) {
        favoriteService.cancel(promptId);
        return Result.success();
    }

    /**
     * 我的收藏分页列表（需要登录）
     * <p>
     * 个人中心"我的收藏"Tab 用这个接口。
     * 返回的是 PromptVO 分页（含分类名、作者名、是否已收藏=true）。
     *
     * @param pageNum  页码，默认 1
     * @param pageSize 每页条数，默认 10
     */
    @Operation(summary = "我的收藏分页列表")
    @GetMapping("/list")
    public Result<Page<PromptVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(favoriteService.myFavorites(pageNum, pageSize));
    }
}
