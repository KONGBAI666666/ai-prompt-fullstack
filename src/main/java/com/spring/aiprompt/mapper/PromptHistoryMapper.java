package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.PromptHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 使用记录表 Mapper
 */
@Mapper
public interface PromptHistoryMapper extends BaseMapper<PromptHistory> {
}
