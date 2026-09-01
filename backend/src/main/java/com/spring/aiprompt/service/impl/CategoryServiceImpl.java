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
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final PromptMapper promptMapper;

    @Override
    public List<Category> listAll() {
        return lambdaQuery().orderByAsc(Category::getId).list();
    }

    @Override
    public void add(CategoryDTO dto) {
        boolean exists = lambdaQuery().eq(Category::getName, dto.getName()).exists();
        if (exists) {
            throw new BusinessException("分类名称已存在");
        }
        Category category = new Category();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        try {
            save(category);
        } catch (DuplicateKeyException e) {
            // 并发下绕过上方查重时，由唯一索引兜底并转为友好提示
            throw new BusinessException("分类名称已存在");
        }
    }

    @Override
    public void removeCategory(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("分类不存在");
        }
        Long count = promptMapper.selectCount(
                Wrappers.<Prompt>lambdaQuery().eq(Prompt::getCategoryId, id));
        if (count != null && count > 0) {
            throw new BusinessException("该分类下还有 " + count + " 条Prompt，不能删除");
        }
        removeById(id);
    }
}
