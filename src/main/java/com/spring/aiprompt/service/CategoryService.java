package com.spring.aiprompt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.spring.aiprompt.dto.CategoryDTO;
import com.spring.aiprompt.entity.Category;

import java.util.List;

/**
 * 分类业务接口
 */
public interface CategoryService extends IService<Category> {

    /** 全部分类（按 id 升序） */
    List<Category> listAll();

    /** 新增分类：名称查重（数据库唯一索引 uk_category_name 兜底） */
    void add(CategoryDTO dto);

    /** 删除分类：分类下仍有 Prompt 时拒绝删除 */
    void removeCategory(Long id);
}
