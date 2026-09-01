package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.PromptHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 使用记录表 Mapper —— 数据访问层接口
 * 继承 BaseMapper&lt;PromptHistory&gt; 自动拥有增删改查能力。
 * 用于 PromptHistoryServiceImpl 记录使用、查询历史，以及 PromptServiceImpl 级联删除。
 */
@Mapper
public interface PromptHistoryMapper extends BaseMapper<PromptHistory> {
}
