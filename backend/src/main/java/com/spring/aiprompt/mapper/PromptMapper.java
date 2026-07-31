package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.Prompt;
import org.apache.ibatis.annotations.Mapper;

/**
 * Prompt 表 Mapper
 */
@Mapper
public interface PromptMapper extends BaseMapper<Prompt> {
}
