package com.spring.aiprompt.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.spring.aiprompt.dto.PromptDTO;
import com.spring.aiprompt.entity.Category;
import com.spring.aiprompt.entity.Favorite;
import com.spring.aiprompt.entity.Prompt;
import com.spring.aiprompt.entity.PromptHistory;
import com.spring.aiprompt.entity.User;
import com.spring.aiprompt.exception.BusinessException;
import com.spring.aiprompt.mapper.CategoryMapper;
import com.spring.aiprompt.mapper.FavoriteMapper;
import com.spring.aiprompt.mapper.PromptHistoryMapper;
import com.spring.aiprompt.mapper.PromptMapper;
import com.spring.aiprompt.mapper.UserMapper;
import com.spring.aiprompt.service.PromptService;
import com.spring.aiprompt.vo.PromptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prompt 业务实现
 */
@Service
@RequiredArgsConstructor
public class PromptServiceImpl extends ServiceImpl<PromptMapper, Prompt> implements PromptService {

    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final FavoriteMapper favoriteMapper;
    private final PromptHistoryMapper promptHistoryMapper;

    @Override
    public void create(PromptDTO dto) {
        checkCategoryExists(dto.getCategoryId());
        Prompt prompt = new Prompt();
        prompt.setTitle(dto.getTitle());
        prompt.setContent(dto.getContent());
        prompt.setDescription(dto.getDescription());
        prompt.setCategoryId(dto.getCategoryId());
        prompt.setUserId(StpUtil.getLoginIdAsLong());
        prompt.setViewCount(0);
        prompt.setFavoriteCount(0);
        prompt.setStatus(1);
        save(prompt);
    }

    @Override
    public Page<PromptVO> pageList(long pageNum, long pageSize, String keyword, Long categoryId, Long onlyUserId) {
        Page<Prompt> page = lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(Prompt::getTitle, keyword)
                        .or()
                        .like(Prompt::getDescription, keyword))
                .eq(categoryId != null, Prompt::getCategoryId, categoryId)
                .eq(onlyUserId != null, Prompt::getUserId, onlyUserId)
                .orderByDesc(Prompt::getCreateTime)
                .page(new Page<>(pageNum, pageSize));
        Page<PromptVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(toVOList(page.getRecords()));
        return voPage;
    }

    @Override
    public PromptVO detail(Long id) {
        Prompt prompt = getById(id);
        if (prompt == null) {
            throw new BusinessException("Prompt不存在");
        }
        // 浏览次数数据库原子 +1（不采用查出改回，避免并发丢更新）
        lambdaUpdate().eq(Prompt::getId, id).setSql("view_count = view_count + 1").update();
        prompt.setViewCount(prompt.getViewCount() == null ? 1 : prompt.getViewCount() + 1);
        return toVOList(List.of(prompt)).get(0);
    }

    @Override
    public void updatePrompt(Long id, PromptDTO dto) {
        Prompt prompt = getById(id);
        if (prompt == null) {
            throw new BusinessException("Prompt不存在");
        }
        // 只能修改自己的 Prompt（管理员只删违规不代改）
        if (!Objects.equals(prompt.getUserId(), StpUtil.getLoginIdAsLong())) {
            throw new BusinessException(403, "只能修改自己的Prompt");
        }
        checkCategoryExists(dto.getCategoryId());
        // 只回写可编辑字段，不触碰 view_count/favorite_count，避免覆盖并发的原子计数
        lambdaUpdate().eq(Prompt::getId, id)
                .set(Prompt::getTitle, dto.getTitle())
                .set(Prompt::getContent, dto.getContent())
                .set(Prompt::getDescription, dto.getDescription())
                .set(Prompt::getCategoryId, dto.getCategoryId())
                .set(Prompt::getUpdateTime, LocalDateTime.now())
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePrompt(Long id) {
        Prompt prompt = getById(id);
        if (prompt == null) {
            throw new BusinessException("Prompt不存在");
        }
        long loginId = StpUtil.getLoginIdAsLong();
        User loginUser = userMapper.selectById(loginId);
        boolean isAdmin = loginUser != null && "ADMIN".equals(loginUser.getRole());
        // 本人或管理员可删
        if (!Objects.equals(prompt.getUserId(), loginId) && !isAdmin) {
            throw new BusinessException(403, "只能删除自己的Prompt");
        }
        removeById(id);
        // 物理删除后级联清理该 Prompt 的收藏与使用记录
        favoriteMapper.delete(Wrappers.<Favorite>lambdaQuery().eq(Favorite::getPromptId, id));
        promptHistoryMapper.delete(Wrappers.<PromptHistory>lambdaQuery().eq(PromptHistory::getPromptId, id));
    }

    @Override
    public List<PromptVO> toVOList(List<Prompt> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            return new ArrayList<>();
        }
        // 批量查分类名与作者名，避免循环内逐条查询（N+1）
        Set<Long> categoryIds = prompts.stream().map(Prompt::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = prompts.stream().map(Prompt::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, String> usernames = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));
        // 当前登录用户已收藏的 promptId 集合
        Set<Long> favoritedIds = new HashSet<>();
        if (StpUtil.isLogin()) {
            List<Long> promptIds = prompts.stream().map(Prompt::getId).toList();
            favoriteMapper.selectList(Wrappers.<Favorite>lambdaQuery()
                            .eq(Favorite::getUserId, StpUtil.getLoginIdAsLong())
                            .in(Favorite::getPromptId, promptIds))
                    .forEach(f -> favoritedIds.add(f.getPromptId()));
        }
        return prompts.stream().map(p -> {
            PromptVO vo = BeanUtil.copyProperties(p, PromptVO.class);
            vo.setCategoryName(categoryNames.get(p.getCategoryId()));
            vo.setUsername(usernames.get(p.getUserId()));
            vo.setFavorited(favoritedIds.contains(p.getId()));
            return vo;
        }).toList();
    }

    /** 校验分类存在，不存在抛业务异常 */
    private void checkCategoryExists(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException("分类不存在");
        }
    }
}
