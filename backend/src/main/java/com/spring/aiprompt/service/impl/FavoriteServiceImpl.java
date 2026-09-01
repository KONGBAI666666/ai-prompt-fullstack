package com.spring.aiprompt.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.spring.aiprompt.entity.Favorite;
import com.spring.aiprompt.entity.Prompt;
import com.spring.aiprompt.exception.BusinessException;
import com.spring.aiprompt.mapper.FavoriteMapper;
import com.spring.aiprompt.mapper.PromptMapper;
import com.spring.aiprompt.service.FavoriteService;
import com.spring.aiprompt.service.PromptService;
import com.spring.aiprompt.vo.PromptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收藏业务实现
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    private final PromptMapper promptMapper;
    private final PromptService promptService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long promptId) {
        Prompt prompt = promptMapper.selectById(promptId);
        if (prompt == null) {
            throw new BusinessException("Prompt不存在");
        }
        long userId = StpUtil.getLoginIdAsLong();
        // 代码层查重，数据库联合唯一索引 uk_user_prompt 兜底并发
        boolean exists = lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getPromptId, promptId)
                .exists();
        if (exists) {
            throw new BusinessException("已收藏过该Prompt");
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setPromptId(promptId);
        try {
            save(favorite);
        } catch (DuplicateKeyException e) {
            // 并发双击绕过上方查重时，由唯一索引兜底并转为业务提示
            throw new BusinessException("已收藏过该Prompt");
        }
        // 收藏数数据库原子 +1
        promptMapper.update(null, Wrappers.<Prompt>lambdaUpdate()
                .eq(Prompt::getId, promptId)
                .setSql("favorite_count = favorite_count + 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long promptId) {
        long userId = StpUtil.getLoginIdAsLong();
        boolean removed = remove(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getPromptId, promptId));
        if (!removed) {
            throw new BusinessException("未收藏该Prompt");
        }
        // 收藏数原子 -1，GREATEST 防止并发下出现负数
        promptMapper.update(null, Wrappers.<Prompt>lambdaUpdate()
                .eq(Prompt::getId, promptId)
                .setSql("favorite_count = GREATEST(favorite_count - 1, 0)"));
    }

    @Override
    public Page<PromptVO> myFavorites(long pageNum, long pageSize) {
        long userId = StpUtil.getLoginIdAsLong();
        // 先分页查收藏记录（按收藏时间倒序），再批量查对应 Prompt 转 VO
        Page<Favorite> favoritePage = lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .page(new Page<>(pageNum, pageSize));
        Page<PromptVO> voPage = new Page<>(favoritePage.getCurrent(), favoritePage.getSize(), favoritePage.getTotal());
        if (favoritePage.getRecords().isEmpty()) {
            voPage.setRecords(new ArrayList<>());
            return voPage;
        }
        List<Long> promptIds = favoritePage.getRecords().stream().map(Favorite::getPromptId).toList();
        List<Prompt> prompts = promptMapper.selectBatchIds(promptIds);
        // 保持收藏时间倒序（selectBatchIds 不保证顺序），用 Map 一次定位，避免 O(n²)
        Map<Long, Prompt> promptMap = prompts.stream()
                .collect(Collectors.toMap(Prompt::getId, p -> p));
        // 删除 Prompt 时已级联清理收藏记录，收藏列表里的 Prompt 必然存在
        List<Prompt> ordered = promptIds.stream()
                .map(promptMap::get)
                .toList();
        voPage.setRecords(promptService.toVOList(ordered));
        return voPage;
    }
}
