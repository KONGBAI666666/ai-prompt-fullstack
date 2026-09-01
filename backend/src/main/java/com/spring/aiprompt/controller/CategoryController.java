package com.spring.aiprompt.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.spring.aiprompt.common.Result;
import com.spring.aiprompt.dto.CategoryDTO;
import com.spring.aiprompt.entity.Category;
import com.spring.aiprompt.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类接口 Controller
 * <p>
 * 路径前缀：/category
 * <p>
 * 权限设计：
 * - GET /category/list：所有登录用户都能查（首页筛选、发布页选择分类都要用）
 * - POST /category：只有管理员能新增（@SaCheckRole("ADMIN")）
 * - DELETE /category/{id}：只有管理员能删除（@SaCheckRole("ADMIN")）
 * <p>
 * @SaCheckRole("ADMIN")：Sa-Token 注解，表示当前登录用户的角色列表中必须包含 "ADMIN" 才能访问，
 * 否则抛 NotRoleException，被 GlobalExceptionHandler 转为 403。
 */
@Tag(name = "分类管理")
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 分类列表（登录即可查）
     * 前端首页分类筛选下拉框、发布页分类选择器都调这个接口
     */
    @Operation(summary = "分类列表")
    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(categoryService.listAll());
    }

    /**
     * 新增分类（管理员专属）
     * @SaCheckRole("ADMIN")：非管理员访问会返回 403
     */
    @Operation(summary = "新增分类（管理员）")
    @SaCheckRole("ADMIN")
    @PostMapping
    public Result<Void> add(@Validated @RequestBody CategoryDTO dto) {
        categoryService.add(dto);
        return Result.success();
    }

    /**
     * 删除分类（管理员专属）
     * Service 层会检查该分类下是否还有 Prompt，有则拒绝删除
     */
    @Operation(summary = "删除分类（管理员）")
    @SaCheckRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        categoryService.removeCategory(id);
        return Result.success();
    }
}
