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
 * 分类接口：列表（登录可见）、新增/删除（仅管理员）
 */
@Tag(name = "分类管理")
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** 分类列表 */
    @Operation(summary = "分类列表")
    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(categoryService.listAll());
    }

    /** 新增分类（管理员） */
    @Operation(summary = "新增分类（管理员）")
    @SaCheckRole("ADMIN")
    @PostMapping
    public Result<Void> add(@Validated @RequestBody CategoryDTO dto) {
        categoryService.add(dto);
        return Result.success();
    }

    /** 删除分类（管理员） */
    @Operation(summary = "删除分类（管理员）")
    @SaCheckRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        categoryService.removeCategory(id);
        return Result.success();
    }
}
