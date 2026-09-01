package com.spring.aiprompt.service.impl;

import cn.dev33.satoken.stp.StpUtil;
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
import org.springframework.beans.BeanUtils;
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
 * Prompt（提示词）业务实现 —— 系统核心业务
 * <p>
 * 职责：创建提示词、分页搜索（关键词+分类组合查询）、详情（浏览数原子+1）、
 *       编辑回填、修改（仅本人）、删除（本人或管理员，事务级联清理）。
 * <p>
 * 继承 ServiceImpl&lt;PromptMapper, Prompt&gt;：自动拥有基于 PromptMapper 的通用增删改查能力。
 * 额外注入了 CategoryMapper、UserMapper、FavoriteMapper、PromptHistoryMapper，
 * 因为 toVOList 方法需要批量查分类名、作者名、当前用户是否已收藏。
 */
@Service
@RequiredArgsConstructor
public class PromptServiceImpl extends ServiceImpl<PromptMapper, Prompt> implements PromptService {

    // —— 依赖注入 ——
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final FavoriteMapper favoriteMapper;
    private final PromptHistoryMapper promptHistoryMapper;

    /**
     * 创建 Prompt
     * <p>
     * 安全设计：user_id 不是前端传的，而是从当前登录 token 中取的（StpUtil.getLoginIdAsLong()）。
     * 这样即使前端伪造 userId，后端也不会信——"不信任前端"原则。
     *
     * @param dto 提示词入参（标题、内容、描述、分类id）
     */
    @Override
    public void create(PromptDTO dto) {
        // 校验分类是否存在（不能往不存在的分类下发提示词）
        checkCategoryExists(dto.getCategoryId());

        // 组装 Prompt 实体
        Prompt prompt = new Prompt();
        prompt.setTitle(dto.getTitle());
        prompt.setContent(dto.getContent());
        prompt.setDescription(dto.getDescription());
        prompt.setCategoryId(dto.getCategoryId());
        // 关键：作者 id 从当前登录 token 取，不信任前端传入
        prompt.setUserId(StpUtil.getLoginIdAsLong());
        prompt.setViewCount(0);       // 新发布浏览数为 0
        prompt.setFavoriteCount(0);  // 新发布收藏数为 0
        prompt.setStatus(1);          // 状态正常

        // save → INSERT INTO prompt (...)，createTime/updateTime 由 MyMetaObjectHandler 自动填充
        save(prompt);
    }

    /**
     * 分页查询 Prompt 列表（统一入口，多个 Controller 复用）
     * <p>
     * 查询条件（全部可选）：
     * - keyword：模糊匹配 title 或 description（LIKE '%keyword%'）
     * - categoryId：精确筛选分类（= categoryId）
     * - onlyUserId：只查某个用户的（/prompt/my 和 /admin/prompt/list 复用此参数）
     * <p>
     * 排序：按创建时间倒序（最新排前面）
     * <p>
     * N+1 查询优化：查完 Prompt 后，批量查分类名和作者名，避免在循环里逐条查。
     *
     * @param pageNum     页码
     * @param pageSize    每页条数
     * @param keyword     关键词（可选，模糊匹配标题和描述）
     * @param categoryId  分类 id（可选，精确筛选）
     * @param onlyUserId  指定用户 id（可选，非空时只查该用户的 Prompt）
     * @return 分页结果，records 里是 PromptVO（含分类名、作者名、是否已收藏）
     */
    @Override
    public Page<PromptVO> pageList(long pageNum, long pageSize, String keyword, Long categoryId, Long onlyUserId) {
        // 链式条件查询：
        // .and(condition, lambda)：condition 为 true 时才拼接条件
        // .like(Prompt::getTitle, keyword) → title LIKE '%keyword%'
        // .or() → OR
        // .like(Prompt::getDescription, keyword) → description LIKE '%keyword%'
        // 组合效果：WHERE (title LIKE '%kw%' OR description LIKE '%kw%')
        // .eq(categoryId != null, Prompt::getCategoryId, categoryId) → category_id = ?（仅当 categoryId 非空时拼接）
        // .eq(onlyUserId != null, Prompt::getUserId, onlyUserId) → user_id = ?（仅当 onlyUserId 非空时拼接）
        Page<Prompt> page = lambdaQuery()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Prompt::getTitle, keyword)
                        .or()
                        .like(Prompt::getDescription, keyword))
                .eq(categoryId != null, Prompt::getCategoryId, categoryId)
                .eq(onlyUserId != null, Prompt::getUserId, onlyUserId)
                .orderByDesc(Prompt::getCreateTime)
                .page(new Page<>(pageNum, pageSize));

        // 把 Prompt 分页转为 PromptVO 分页
        Page<PromptVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(toVOList(page.getRecords()));
        return voPage;
    }

    /**
     * 获取 Prompt 详情（浏览数 +1）
     * <p>
     * 与 getForEdit 的区别：这个方法会触发浏览计数 +1，用于用户点进详情页时调用。
     * 浏览数使用数据库原子操作（setSql("view_count = view_count + 1")），
     * 而不是"查出→+1→写回"，后者在并发下会丢失更新。
     * <p>
     * 并发场景举例：100 个用户同时打开同一篇 Prompt，
     * 用"查出→改→写回"的方式：100 个线程都读到 view_count=10，都写回 11，结果只 +1。
     * 用原子 SQL：100 条 UPDATE view_count = view_count + 1 并发执行，结果正确 +100。
     *
     * @param id Prompt id
     * @return PromptVO（含分类名、作者名、是否已收藏）
     */
    @Override
    public PromptVO detail(Long id) {
        Prompt prompt = getById(id);
        if (prompt == null) {
            throw new BusinessException("Prompt不存在");
        }
        // 原子递增浏览数：UPDATE prompt SET view_count = view_count + 1 WHERE id = ?
        // 数据库引擎保证单条 UPDATE 语句的原子性，不会丢失更新
        lambdaUpdate().eq(Prompt::getId, id).setSql("view_count = view_count + 1").update();
        // 内存中的 prompt 对象也同步 +1（用于返回给前端的 VO）
        prompt.setViewCount(prompt.getViewCount() + 1);
        return toVOList(List.of(prompt)).get(0);
    }

    /**
     * 编辑回填：只查详情，不增加浏览数
     * <p>
     * 用户点"编辑"按钮时，需要先获取 Prompt 当前内容填到表单里。
     * 如果用 detail() 方法，每次进编辑页浏览数都会 +1，数据就不准了。
     * 所以专门提供 getForEdit()，只读不加计数。
     *
     * @param id Prompt id
     * @return PromptVO
     */
    @Override
    public PromptVO getForEdit(Long id) {
        Prompt prompt = getById(id);
        if (prompt == null) {
            throw new BusinessException("Prompt不存在");
        }
        // 不触碰浏览计数
        return toVOList(List.of(prompt)).get(0);
    }

    /**
     * 修改 Prompt（仅本人可改）
     * <p>
     * 权限规则：只能修改自己的 Prompt，管理员也不能代改（管理员只删违规内容，不代改内容）。
     * 安全设计：用 lambdaUpdate 只回写可编辑字段（title/content/description/categoryId），
     * 不触碰 view_count 和 favorite_count —— 避免覆盖并发的原子计数。
     * <p>
     * 如果用 updateById(prompt)（整条覆盖），用户在编辑页打开后，
     * 如果此时有别人浏览了这篇 Prompt（view_count +1），
     * 用户保存编辑后，updateById 会用旧的 view_count 覆盖数据库里已经 +1 后的值，
     * 导致浏览数丢失更新。用 lambdaUpdate 指定字段就不会有这个问题。
     *
     * @param id  Prompt id
     * @param dto 修改入参
     */
    @Override
    public void updatePrompt(Long id, PromptDTO dto) {
        Prompt prompt = getById(id);
        if (prompt == null) {
            throw new BusinessException("Prompt不存在");
        }
        // 权限校验：只能改自己的 Prompt
        // StpUtil.getLoginIdAsLong() 从 token 取当前登录人 id
        if (!Objects.equals(prompt.getUserId(), StpUtil.getLoginIdAsLong())) {
            // 403 = 无权限
            throw new BusinessException(403, "只能修改自己的Prompt");
        }
        // 校验分类存在
        checkCategoryExists(dto.getCategoryId());
        // 只更新可编辑字段，不触碰 view_count / favorite_count
        // 等价于：UPDATE prompt SET title=?, content=?, description=?, category_id=?, update_time=? WHERE id=?
        lambdaUpdate().eq(Prompt::getId, id)
                .set(Prompt::getTitle, dto.getTitle())
                .set(Prompt::getContent, dto.getContent())
                .set(Prompt::getDescription, dto.getDescription())
                .set(Prompt::getCategoryId, dto.getCategoryId())
                .set(Prompt::getUpdateTime, LocalDateTime.now())
                .update();
    }

    /**
     * 删除 Prompt（本人或管理员，事务级联清理）
     * <p>
     * 三大设计点：
     * 1. 数据级权限：不是谁都能删，先判断"是本人 或 是管理员"才放行，否则 403
     * 2. 事务 @Transactional：删 Prompt → 删收藏 → 删使用记录，三步要么全成功要么全回滚
     * 3. 级联清理：主表删了，从表（favorite、prompt_history）里的关联数据也要删，
     *    否则会留下"孤儿数据"（收藏记录指向不存在的 Prompt）
     *
     * @param id Prompt id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePrompt(Long id) {
        Prompt prompt = getById(id);
        if (prompt == null) {
            throw new BusinessException("Prompt不存在");
        }

        // 获取当前登录用户，判断是否有删除权限
        long loginId = StpUtil.getLoginIdAsLong();
        User loginUser = userMapper.selectById(loginId);
        // 管理员可以删任何人的 Prompt（用于删除违规内容）
        boolean isAdmin = loginUser != null && "ADMIN".equals(loginUser.getRole());

        // 权限校验：只有作者本人或管理员可以删除
        if (!Objects.equals(prompt.getUserId(), loginId) && !isAdmin) {
            throw new BusinessException(403, "只能删除自己的Prompt");
        }

        // —— 以下三步在同一个事务中，任何一步失败都整体回滚 ——

        // 第 1 步：删除 Prompt 主表记录
        // removeById → DELETE FROM prompt WHERE id = ?
        removeById(id);

        // 第 2 步：级联删除收藏记录
        // Wrappers.<Favorite>lambdaQuery() → 构造 LambdaQueryWrapper
        // .eq(Favorite::getPromptId, id) → WHERE prompt_id = ?
        // favoriteMapper.delete(wrapper) → DELETE FROM favorite WHERE prompt_id = ?
        favoriteMapper.delete(Wrappers.<Favorite>lambdaQuery().eq(Favorite::getPromptId, id));

        // 第 3 步：级联删除使用记录
        // DELETE FROM prompt_history WHERE prompt_id = ?
        promptHistoryMapper.delete(Wrappers.<PromptHistory>lambdaQuery().eq(PromptHistory::getPromptId, id));
    }

    /**
     * 批量把 Prompt 列表转换为 PromptVO 列表
     * <p>
     * 这是 N+1 查询优化的关键方法。如果不用批量查，循环里逐条查分类名和作者名：
     *   10 条 Prompt → 1 次查列表 + 10 次查分类 + 10 次查作者 = 21 次查询（N+1 问题）
     * 批量查后：
     *   10 条 Prompt → 1 次查列表 + 1 次批量查分类 + 1 次批量查作者 + 1 次批量查收藏 = 4 次查询
     *
     * 补充信息：
     * - categoryName：从 Category 表批量查（selectBatchIds）
     * - username：从 User 表批量查
     * - favorited：从 Favorite 表批量查当前用户是否收藏了这些 Prompt
     *
     * @param prompts Prompt 实体列表
     * @return PromptVO 列表
     */
    @Override
    public List<PromptVO> toVOList(List<Prompt> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            return new ArrayList<>();
        }

        // —— 批量查分类名 ——
        // 收集所有 Prompt 的 categoryId（去重、去 null）
        Set<Long> categoryIds = prompts.stream().map(Prompt::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        // 收集所有 Prompt 的 userId（去重、去 null）
        Set<Long> userIds = prompts.stream().map(Prompt::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        // selectBatchIds：SELECT * FROM category WHERE id IN (?, ?, ?)
        // → 转成 Map<categoryId, categoryName>，后续用 O(1) 查找
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));

        // 同理，批量查作者名 → Map<userId, username>
        Map<Long, String> usernames = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));

        // —— 批量查当前用户是否已收藏这些 Prompt ——
        Set<Long> favoritedIds = new HashSet<>();
        if (StpUtil.isLogin()) {
            // 收集所有 Prompt 的 id
            List<Long> promptIds = prompts.stream().map(Prompt::getId).toList();
            // SELECT prompt_id FROM favorite WHERE user_id = ? AND prompt_id IN (?, ?, ...)
            favoriteMapper.selectList(Wrappers.<Favorite>lambdaQuery()
                            .eq(Favorite::getUserId, StpUtil.getLoginIdAsLong())
                            .in(Favorite::getPromptId, promptIds))
                    .forEach(f -> favoritedIds.add(f.getPromptId()));
        }

        // —— 组装 VO ——
        return prompts.stream().map(p -> {
            PromptVO vo = new PromptVO();
            // BeanUtils.copyProperties：按字段名复制 Prompt 的属性到 PromptVO
            BeanUtils.copyProperties(p, vo);
            // 补充分类名（从 Map 取，O(1)）
            vo.setCategoryName(categoryNames.get(p.getCategoryId()));
            // 补充作者名
            vo.setUsername(usernames.get(p.getUserId()));
            // 补充是否已收藏
            vo.setFavorited(favoritedIds.contains(p.getId()));
            return vo;
        }).toList();
    }

    /**
     * 校验分类是否存在
     * 不存在则抛业务异常（防止给 Prompt 指定一个不存在的分类）
     *
     * @param categoryId 分类 id
     */
    private void checkCategoryExists(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException("分类不存在");
        }
    }
}
