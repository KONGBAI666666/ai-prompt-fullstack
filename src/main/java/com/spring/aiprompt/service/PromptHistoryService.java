package com.spring.aiprompt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.spring.aiprompt.entity.PromptHistory;
import com.spring.aiprompt.vo.HistoryVO;

/**
 * 使用记录业务接口
 */
public interface PromptHistoryService extends IService<PromptHistory> {

    /** 记录一次使用（"复制Prompt"时调用） */
    void record(Long promptId);

    /** 当前用户使用记录分页（含 Prompt 标题） */
    Page<HistoryVO> myHistory(long pageNum, long pageSize);
}
