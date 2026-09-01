package com.spring.aiprompt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.spring.aiprompt.dto.CategoryDTO;
import com.spring.aiprompt.entity.Category;
import com.spring.aiprompt.entity.Prompt;
import com.spring.aiprompt.exception.BusinessException;
import com.spring.aiprompt.mapper.CategoryMapper;
import com.spring.aiprompt.mapper.PromptMapper;
import com.spring.aiprompt.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类业务实现
 * <p>
 * 职责：全量查询分类列表、新增分类（查重+唯一索引兜底）、删除分类（检查是否还有 Prompt 引用）。
 * <p>
 * 设计亮点：
 * - 新增分类时：代码层查重 + 数据库唯一索引 uk_category_name 双重防护
 * - 删除分类时：检查该分类下是否还有 Prompt，有则拒绝删除（防止数据完整性被破坏）
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final PromptMapper promptMapper;

    /**
     * 查询全部分类（按 id 升序）
     * 前端首页的分类筛选下拉框、发布页的分类选择器都用这个接口
     *
     * @return 分类列表
     */
    @Override
    public List<Category> listAll() {
        // SELECT * FROM category ORDER BY id ASC
        return lambdaQuery().orderByAsc(Category::getId).list();
    }

    /**
     * 新增分类
     * <p>
     * 查重策略：
     * 1. 代码层先查：SELECT COUNT(*) FROM category WHERE name = ?，有则提示"已存在"
     * 2. 数据库唯一索引 uk_category_name 兜底：并发下两个请求同时通过查重，
     *    但数据库只允许一条插入成功，另一条抛 DuplicateKeyException
     *
     * @param dto 分类入参（名称、描述）
     */
    @Override
    public void add(CategoryDTO dto) {
        // 代码层查重
        boolean exists = lambdaQuery().eq(Category::getName, dto.getName()).exists();
        if (exists) {
            throw new BusinessException("分类名称已存在");
        }

        // 组装并插入
        Category category = new Category();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        try {
            save(category); // INSERT INTO category (name, description) VALUES (?, ?)
        } catch (DuplicateKeyException e) {
            // 并发兜底
            throw new BusinessException("分类名称已存在");
        }
    }

    /**
     * 删除分类
     * <p>
     * 安全检查：该分类下如果还有 Prompt，拒绝删除。
     * 原因：如果允许删除，那些 Prompt 的 category_id 会变成悬空引用（指向不存在的分类），
     * 导致前端展示分类名时取不到（null），用户体验和数据完整性都受影响。
     * <p>
     * 正确做法：先让管理员把该分类下的 Prompt 迁移到其他分类或删除，再删分类。
     *
     * @param id 要删除的分类 id
     */
    @Override
    public void removeCategory(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("分类不存在");
        }
        // 查该分类下有多少条 Prompt
        Long count = promptMapper.selectCount(
                Wrappers.<Prompt>lambdaQuery().eq(Prompt::getCategoryId, id));
        if (count != null && count > 0) {
            throw new BusinessException("该分类下还有 " + count + " 条Prompt，不能删除");
        }
        // 没有引用了，安全删除
        removeById(id); // DELETE FROM category WHERE id = ?
    }
}
