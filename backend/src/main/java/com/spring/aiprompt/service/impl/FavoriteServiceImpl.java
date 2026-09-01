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
 * 收藏业务实现 —— 并发安全的收藏/取消收藏
 * <p>
 * 设计亮点：
 * 1. 防重复收藏：代码层查重 + 数据库联合唯一索引（uk_user_prompt）双重防护
 * 2. 收藏数原子操作：favorite_count +1 用 SQL 原子语句，不用"查出→改→写回"
 * 3. 防负数：取消收藏时用 GREATEST(favorite_count - 1, 0)，保证计数不为负
 * 4. 全程事务：收藏/取消收藏涉及两张表（favorite + prompt），用 @Transactional 保证一致性
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    private final PromptMapper promptMapper;
    private final PromptService promptService;

    /**
     * 收藏 Prompt
     * <p>
     * 流程：校验 Prompt 存在 → 查重（是否已收藏）→ 插入收藏记录 → 收藏数原子 +1
     * <p>
     * 并发安全：
     * - 代码层查重可能在并发双击时都返回"未收藏"
     * - 数据库联合唯一索引 (user_id, prompt_id) 是最后兜底，第二个插入会抛 DuplicateKeyException
     * - catch 后转为业务提示，用户看到"已收藏过"，不会看到 500 堆栈
     *
     * @param promptId 要收藏的 Prompt id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long promptId) {
        // 校验 Prompt 存在
        Prompt prompt = promptMapper.selectById(promptId);
        if (prompt == null) {
            throw new BusinessException("Prompt不存在");
        }

        // 当前登录用户 id
        long userId = StpUtil.getLoginIdAsLong();

        // 代码层查重：SELECT COUNT(*) FROM favorite WHERE user_id=? AND prompt_id=?
        boolean exists = lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getPromptId, promptId)
                .exists();
        if (exists) {
            throw new BusinessException("已收藏过该Prompt");
        }

        // 组装并插入收藏记录
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setPromptId(promptId);
        try {
            save(favorite); // INSERT INTO favorite (user_id, prompt_id) VALUES (?, ?)
        } catch (DuplicateKeyException e) {
            // 并发双击兜底：两个请求同时通过上面的查重，但数据库唯一索引只允许一条插入成功
            throw new BusinessException("已收藏过该Prompt");
        }

        // 收藏数原子 +1：UPDATE prompt SET favorite_count = favorite_count + 1 WHERE id = ?
        // 不用"查出 favorite_count → +1 → 写回"，后者在并发下会丢失更新
        promptMapper.update(null, Wrappers.<Prompt>lambdaUpdate()
                .eq(Prompt::getId, promptId)
                .setSql("favorite_count = favorite_count + 1"));
    }

    /**
     * 取消收藏
     * <p>
     * 流程：删除收藏记录 → 收藏数原子 -1（GREATEST 防负数）
     * <p>
     * 防负数设计：
     * 如果直接 favorite_count - 1，在极端并发场景下可能出现 0 - 1 = -1 的情况
     * （两个线程同时取消收藏，但实际只有一条收藏记录）。
     * 用 GREATEST(favorite_count - 1, 0)：如果结果 < 0，就取 0，保证计数永远非负。
     *
     * @param promptId 要取消收藏的 Prompt id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long promptId) {
        long userId = StpUtil.getLoginIdAsLong();

        // 删除收藏记录：DELETE FROM favorite WHERE user_id=? AND prompt_id=?
        boolean removed = remove(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getPromptId, promptId));
        if (!removed) {
            throw new BusinessException("未收藏该Prompt");
        }

        // 收藏数原子 -1，GREATEST 防止出现负数
        // UPDATE prompt SET favorite_count = GREATEST(favorite_count - 1, 0) WHERE id = ?
        promptMapper.update(null, Wrappers.<Prompt>lambdaUpdate()
                .eq(Prompt::getId, promptId)
                .setSql("favorite_count = GREATEST(favorite_count - 1, 0)"));
    }

    /**
     * 我的收藏分页列表
     * <p>
     * 查询策略：先分页查收藏记录（按收藏时间倒序），再批量查对应的 Prompt 转 VO。
     * <p>
     * 顺序保持问题：
     * selectBatchIds 不保证返回顺序（它按 id 排序），但用户希望按收藏时间倒序展示。
     * 解决：先用 Map 把 promptId → Prompt 建立映射，再按收藏记录的顺序去 Map 里取，
     * 这样就能保持收藏时间倒序，且查 Prompt 只需一次批量查询。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果，records 里是 PromptVO
     */
    @Override
    public Page<PromptVO> myFavorites(long pageNum, long pageSize) {
        long userId = StpUtil.getLoginIdAsLong();

        // 第 1 步：分页查收藏记录
        Page<Favorite> favoritePage = lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .page(new Page<>(pageNum, pageSize));

        // 构造返回的分页对象（保留分页信息）
        Page<PromptVO> voPage = new Page<>(favoritePage.getCurrent(), favoritePage.getSize(), favoritePage.getTotal());
        if (favoritePage.getRecords().isEmpty()) {
            voPage.setRecords(new ArrayList<>());
            return voPage;
        }

        // 第 2 步：从收藏记录中提取 promptId 列表
        List<Long> promptIds = favoritePage.getRecords().stream().map(Favorite::getPromptId).toList();

        // 第 3 步：批量查 Prompt
        List<Prompt> prompts = promptMapper.selectBatchIds(promptIds);

        // 第 4 步：建立 Map<promptId, Prompt>，用于按收藏时间顺序查找
        Map<Long, Prompt> promptMap = prompts.stream()
                .collect(Collectors.toMap(Prompt::getId, p -> p));

        // 第 5 步：按收藏记录的顺序去 Map 取 Prompt，保持收藏时间倒序
        // 注意：删除 Prompt 时已级联清理收藏记录（见 PromptServiceImpl.deletePrompt），
        // 所以收藏列表里的 promptId 在 Map 中必然存在
        List<Prompt> ordered = promptIds.stream()
                .map(promptMap::get)
                .toList();

        // 第 6 步：转 VO（复用 PromptService.toVOList，批量补分类名/作者名/是否收藏）
        voPage.setRecords(promptService.toVOList(ordered));
        return voPage;
    }
}
