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
 * Prompt（提示词）接口 Controller
 * <p>
 * 路径前缀：/prompt
 * 所有接口都需要登录（Sa-Token 全局过滤器校验，不在白名单中）。
 * <p>
 * RESTful 设计：
 * - POST   /prompt        → 创建
 * - GET    /prompt/list   → 分页搜索列表
 * - GET    /prompt/my     → 我的列表
 * - GET    /prompt/{id}   → 详情（浏览数+1）
 * - GET    /prompt/{id}/edit → 编辑回填（浏览数不变）
 * - PUT    /prompt/{id}   → 修改
 * - DELETE /prompt/{id}   → 删除
 */
@Tag(name = "Prompt管理")
@RestController
@RequestMapping("/prompt")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    /**
     * 创建 Prompt（需要登录）
     * <p>
     * @Validated 校验 PromptDTO（标题非空、内容非空、分类非空）。
     * 作者 id 在 Service 层从 token 取，不信任前端传入。
     */
    @Operation(summary = "创建Prompt")
    @PostMapping
    public Result<Void> create(@Validated @RequestBody PromptDTO dto) {
        promptService.create(dto);
        return Result.success();
    }

    /**
     * 分页搜索列表（需要登录）
     * <p>
     * 支持组合查询：
     * - keyword：模糊匹配标题或描述（LIKE '%keyword%'）
     * - categoryId：精确筛选分类
     * <p>
     * @RequestParam(required = false)：参数可选，不传则为 null
     * @RequestParam(defaultValue = "1")：参数有默认值
     *
     * @param pageNum    页码，默认 1
     * @param pageSize   每页条数，默认 10
     * @param keyword    关键词（可选）
     * @param categoryId  分类 id（可选）
     */
    @Operation(summary = "Prompt分页搜索列表")
    @GetMapping("/list")
    public Result<Page<PromptVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Long categoryId) {
        // onlyUserId 传 null：首页列表查所有人的 Prompt
        return Result.success(promptService.pageList(pageNum, pageSize, keyword, categoryId, null));
    }

    /**
     * 我的 Prompt 列表（需要登录）
     * <p>
     * 只查当前登录用户的 Prompt。
     * 复用 pageList 方法，把 onlyUserId 设为当前登录人 id。
     */
    @Operation(summary = "我的Prompt分页列表")
    @GetMapping("/my")
    public Result<Page<PromptVO>> my(@RequestParam(defaultValue = "1") long pageNum,
                                     @RequestParam(defaultValue = "10") long pageSize) {
        // StpUtil.getLoginIdAsLong()：从 token 取当前用户 id
        return Result.success(promptService.pageList(pageNum, pageSize, null, null, StpUtil.getLoginIdAsLong()));
    }

    /**
     * Prompt 详情（浏览数 +1）
     * <p>
     * 用户点进详情页时调用。view_count 使用数据库原子操作递增。
     *
     * @param id Prompt id（路径参数）
     */
    @Operation(summary = "Prompt详情（浏览次数+1）")
    @GetMapping("/{id}")
    public Result<PromptVO> detail(@PathVariable Long id) {
        return Result.success(promptService.detail(id));
    }

    /**
     * 编辑回填详情（不增加浏览数）
     * <p>
     * 用户点"编辑"按钮时，前端先调这个接口获取当前内容填到表单里。
     * 和 detail 的区别：不触发 view_count +1。
     *
     * @param id Prompt id
     */
    @Operation(summary = "编辑回填详情（不增加浏览次数）")
    @GetMapping("/{id}/edit")
    public Result<PromptVO> detailForEdit(@PathVariable Long id) {
        return Result.success(promptService.getForEdit(id));
    }

    /**
     * 修改 Prompt（仅本人）
     * <p>
     * 权限：只有作者本人能修改自己的 Prompt。
     * Service 层会校验 prompt.userId 是否等于当前登录人 id。
     */
    @Operation(summary = "修改自己的Prompt")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Validated @RequestBody PromptDTO dto) {
        promptService.updatePrompt(id, dto);
        return Result.success();
    }

    /**
     * 删除 Prompt（本人或管理员）
     * <p>
     * 权限：作者本人 或 管理员（管理员可删违规内容）。
     * 删除时级联清理收藏和使用记录（事务保证一致性）。
     */
    @Operation(summary = "删除Prompt（本人或管理员）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptService.deletePrompt(id);
        return Result.success();
    }
}
