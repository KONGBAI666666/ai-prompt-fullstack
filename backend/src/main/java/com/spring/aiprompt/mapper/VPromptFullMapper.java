package com.spring.aiprompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spring.aiprompt.entity.VPromptFull;
import org.apache.ibatis.annotations.Mapper;

/**
 * v_prompt_full 视图 Mapper —— 数据访问层接口
 * <p>
 * 继承 BaseMapper&lt;VPromptFull&gt; 获得基于视图的查询能力。
 * 仅供管理后台的内容列表（/admin/prompt/list）与数据导出（/admin/prompt/export）使用，
 * 视图已关联好分类名与作者名，一条 SQL 替代"查主表 + 批量回查分类/作者"。
 * <p>
 * ⚠️ 视图只读：不要通过该 Mapper 执行写操作（视图无主键约束，写入行为未定义）。
 */
@Mapper
public interface VPromptFullMapper extends BaseMapper<VPromptFull> {
}
