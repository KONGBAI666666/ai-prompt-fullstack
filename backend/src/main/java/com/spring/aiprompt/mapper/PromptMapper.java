package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.Prompt;
import org.apache.ibatis.annotations.Mapper;

/**
 * Prompt（提示词）表 Mapper —— 数据访问层接口
 * 继承 BaseMapper&lt;Prompt&gt; 自动拥有增删改查能力。
 * 用于 PromptServiceImpl 中直接操作 prompt 表（如原子递增 view_count / favorite_count）。
 */
@Mapper
public interface PromptMapper extends BaseMapper<Prompt> {
}
