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
 * <p>
 * 职责：记录用户"复制使用"提示词的行为，以及查询当前用户的使用历史。
 * <p>
 * 使用场景：用户在详情页点"复制"按钮 → 前端调 POST /history/{promptId} → 后端记录一条使用记录。
 * 个人中心"使用记录"Tab 展示该用户的使用历史。
 */
@Service
@RequiredArgsConstructor
public class PromptHistoryServiceImpl extends ServiceImpl<PromptHistoryMapper, PromptHistory>
        implements PromptHistoryService {

    private final PromptMapper promptMapper;

    /**
     * 记录一次使用
     * <p>
     * 用户点"复制 Prompt"时调用，往 prompt_history 表插一条记录。
     * use_time 字段由 MyMetaObjectHandler.insertFill 自动填充（不需要手动赋值）。
     *
     * @param promptId 被使用的 Prompt id
     */
    @Override
    public void record(Long promptId) {
        // 校验 Prompt 存在（不能记录对不存在 Prompt 的使用）
        if (promptMapper.selectById(promptId) == null) {
            throw new BusinessException("Prompt不存在");
        }
        PromptHistory history = new PromptHistory();
        // 使用人：从当前登录 token 取
        history.setUserId(StpUtil.getLoginIdAsLong());
        history.setPromptId(promptId);
        // use_time 由 MyMetaObjectHandler 自动填充（见 handler/MyMetaObjectHandler.java）
        save(history); // INSERT INTO prompt_history (user_id, prompt_id, use_time) VALUES (?, ?, ?)
    }

    /**
     * 我的的使用记录分页列表
     * <p>
     * 和收藏列表类似的 N+1 优化策略：
     * 1. 先分页查使用记录（按使用时间倒序）
     * 2. 批量查 Prompt 标题
     * 3. 组装 HistoryVO
     * <p>
     * 数据完整性保证：删除 Prompt 时已在同一事务中级联清理 prompt_history（见 PromptServiceImpl.deletePrompt），
     * 所以使用记录里的 promptId 在 Prompt 表中必然存在。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果，records 里是 HistoryVO
     */
    @Override
    public Page<HistoryVO> myHistory(long pageNum, long pageSize) {
        // 分页查使用记录：SELECT * FROM prompt_history WHERE user_id=? ORDER BY use_time DESC LIMIT ?,?
        Page<PromptHistory> page = lambdaQuery()
                .eq(PromptHistory::getUserId, StpUtil.getLoginIdAsLong())
                .orderByDesc(PromptHistory::getUseTime)
                .page(new Page<>(pageNum, pageSize));

        Page<HistoryVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (page.getRecords().isEmpty()) {
            voPage.setRecords(new ArrayList<>());
            return voPage;
        }

        // 批量查 Prompt 标题（N+1 优化）
        // 收集所有 promptId（去重）
        Set<Long> promptIds = page.getRecords().stream()
                .map(PromptHistory::getPromptId).collect(Collectors.toSet());
        // 批量查 Prompt → Map<promptId, Prompt>，后续用 O(1) 查找
        Map<Long, Prompt> promptMap = promptMapper.selectBatchIds(promptIds).stream()
                .collect(Collectors.toMap(Prompt::getId, Function.identity()));

        // 组装 HistoryVO
        voPage.setRecords(page.getRecords().stream().map(h -> {
            HistoryVO vo = new HistoryVO();
            vo.setId(h.getId());
            vo.setPromptId(h.getPromptId());
            // 从 Map 取 Prompt 标题
            vo.setPromptTitle(promptMap.get(h.getPromptId()).getTitle());
            vo.setUseTime(h.getUseTime());
            return vo;
        }).toList());
        return voPage;
    }
}
