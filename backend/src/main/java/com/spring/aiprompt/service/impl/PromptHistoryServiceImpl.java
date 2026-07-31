package com.spring.aiprompt.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.spring.aiprompt.entity.Prompt;
import com.spring.aiprompt.entity.PromptHistory;
import com.spring.aiprompt.exception.BusinessException;
import com.spring.aiprompt.mapper.PromptHistoryMapper;
import com.spring.aiprompt.mapper.PromptMapper;
import com.spring.aiprompt.service.PromptHistoryService;
import com.spring.aiprompt.vo.HistoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 使用记录业务实现
 */
@Service
@RequiredArgsConstructor
public class PromptHistoryServiceImpl extends ServiceImpl<PromptHistoryMapper, PromptHistory>
        implements PromptHistoryService {

    private final PromptMapper promptMapper;

    @Override
    public void record(Long promptId) {
        if (promptMapper.selectById(promptId) == null) {
            throw new BusinessException("Prompt不存在");
        }
        PromptHistory history = new PromptHistory();
        history.setUserId(StpUtil.getLoginIdAsLong());
        history.setPromptId(promptId);
        // use_time 由 MyMetaObjectHandler 自动填充
        save(history);
    }

    @Override
    public Page<HistoryVO> myHistory(long pageNum, long pageSize) {
        Page<PromptHistory> page = lambdaQuery()
                .eq(PromptHistory::getUserId, StpUtil.getLoginIdAsLong())
                .orderByDesc(PromptHistory::getUseTime)
                .page(new Page<>(pageNum, pageSize));
        Page<HistoryVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (page.getRecords().isEmpty()) {
            voPage.setRecords(new ArrayList<>());
            return voPage;
        }
        // 批量查 Prompt 标题（Prompt 可能已被物理删除，删除的显示占位标题）
        Set<Long> promptIds = page.getRecords().stream()
                .map(PromptHistory::getPromptId).collect(Collectors.toSet());
        Map<Long, Prompt> promptMap = promptMapper.selectBatchIds(promptIds).stream()
                .collect(Collectors.toMap(Prompt::getId, Function.identity()));
        voPage.setRecords(page.getRecords().stream().map(h -> {
            HistoryVO vo = new HistoryVO();
            vo.setId(h.getId());
            vo.setPromptId(h.getPromptId());
            Prompt prompt = promptMap.get(h.getPromptId());
            vo.setPromptTitle(prompt != null ? prompt.getTitle() : "[已删除]");
            vo.setUseTime(h.getUseTime());
            return vo;
        }).toList());
        return voPage;
    }
}
