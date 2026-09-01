package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类表 Mapper —— 数据访问层接口
 * 继承 BaseMapper&lt;Category&gt; 自动拥有增删改查能力。
 * 用于 CategoryServiceImpl 查询分类列表、新增分类、删除分类。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
