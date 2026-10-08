package com.spring.aiprompt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spring.aiprompt.dto.CategoryDTO;
import com.spring.aiprompt.entity.Category;
import com.spring.aiprompt.entity.Prompt;
import com.spring.aiprompt.exception.BusinessException;
import com.spring.aiprompt.mapper.CategoryMapper;
import com.spring.aiprompt.mapper.PromptMapper;
import com.spring.aiprompt.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 分类业务实现
 * <p>
 * 职责：全量查询分类列表（Redis 缓存）、新增分类（查重+唯一索引兜底+缓存失效）、
 *       删除分类（检查是否还有 Prompt 引用 + 缓存失效）。
 * <p>
 * 设计亮点：
 * - 新增分类时：代码层查重 + 数据库唯一索引 uk_category_name 双重防护
 * - 删除分类时：检查该分类下是否还有 Prompt，有则拒绝删除（防止数据完整性被破坏）
 * <p>
 * 【缓存设计：Cache Aside 旁路缓存模式】
 * 分类列表是典型的"读多写少"数据（首页/发布页/管理页每次打开都查，但分类几个月才改一次），
 * 用 Redis 把重复查询挡在数据库之外：
 * - 读：先查缓存 → 命中直接返回；未命中查库 → 回填缓存（带 TTL）
 * - 写：先更新数据库 → 再删除缓存（而不是更新缓存，避免并发写的时序错乱）
 * <p>
 * 为什么手动实现而不用 @Cacheable 注解：
 * 注解基于 AOP 代理，只拦截跨 Bean 的调用（Controller → Service），
 * 同类内部调用（this.listAll()）不走代理、缓存静默失效，是隐形的坑；
 * 手动实现逻辑可见、行为可控，面试也能把每一步讲清楚。
 * <p>
 * 【防雪崩】TTL 加了 0~5 分钟随机抖动：避免一批 key 同一时刻集中过期，
 * 瞬间全部回源数据库造成压力尖峰。
 * 【防穿透】空列表也缓存：表被清空时，恶意刷新页面不会每次都打到数据库
 * （null 不缓存，空 List 正常缓存，短 TTL 让管理员新增分类后最多 10 分钟自动可见——
 * 另外新增/删除操作会主动清缓存，正常路径无需等 TTL）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final PromptMapper promptMapper;
    private final StringRedisTemplate redis;

    /** 缓存 key：分类列表全量只有一份，用固定 key */
    private static final String CACHE_KEY = "category:list:all";
    /** 基础 TTL：10 分钟 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /**
     * 序列化器：Category 含 LocalDateTime 字段，
     * 必须注册 JavaTimeModule 否则 Jackson 报"不支持 Java 8 日期"错误；
     * 关闭"日期写成时间戳"，输出 ISO 字符串便于人工排查缓存内容。
     */
    private final ObjectMapper cacheMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 查询全部分类（按 id 升序）—— 带 Redis 缓存
     * 前端首页的分类筛选下拉框、发布页的分类选择器都用这个接口
     *
     * @return 分类列表
     */
    @Override
    public List<Category> listAll() {
        // —— 1. 先查缓存 ——
        String cached = redis.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            try {
                List<Category> list = cacheMapper.readValue(cached, new TypeReference<>() {});
                log.debug("分类列表命中缓存，共 {} 条", list.size());
                return list;
            } catch (Exception e) {
                // 反序列化失败（比如实体字段结构变了）：当作未命中，走数据库并覆盖旧缓存
                log.warn("分类缓存反序列化失败，回源数据库: {}", e.getMessage());
            }
        }

        // —— 2. 未命中：查数据库 ——
        // SELECT * FROM category ORDER BY id ASC
        List<Category> list = lambdaQuery().orderByAsc(Category::getId).list();

        // —— 3. 回填缓存（空列表也缓存，防穿透；TTL 加随机抖动防雪崩） ——
        try {
            long jitterSeconds = ThreadLocalRandom.current().nextLong(0, 300);
            redis.opsForValue().set(CACHE_KEY, cacheMapper.writeValueAsString(list),
                    CACHE_TTL.plusSeconds(jitterSeconds));
        } catch (Exception e) {
            // 缓存写入失败不影响主流程（Redis 故障时退化为直查数据库）
            log.warn("分类缓存写入失败: {}", e.getMessage());
        }
        return list;
    }

    /**
     * 新增分类
     * <p>
     * 查重策略：
     * 1. 代码层先查：SELECT COUNT(*) FROM category WHERE name = ?，有则提示"已存在"
     * 2. 数据库唯一索引 uk_category_name 兜底：并发下两个请求同时通过查重，
     *    但数据库只允许一条插入成功，另一条抛 DuplicateKeyException
     *
     * @param dto 分类入参（名称、描述）
     */
    @Override
    public void add(CategoryDTO dto) {
        // 代码层查重
        boolean exists = lambdaQuery().eq(Category::getName, dto.getName()).exists();
        if (exists) {
            throw new BusinessException("分类名称已存在");
        }

        // 组装并插入
        Category category = new Category();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        try {
            save(category); // INSERT INTO category (name, description) VALUES (?, ?)
        } catch (DuplicateKeyException e) {
            // 并发兜底
            throw new BusinessException("分类名称已存在");
        }
        // Cache Aside 写路径：库已更新 → 删除缓存（下次读自然回填最新数据）
        redis.delete(CACHE_KEY);
    }

    /**
     * 删除分类
     * <p>
     * 安全检查：该分类下如果还有 Prompt，拒绝删除。
     * 原因：如果允许删除，那些 Prompt 的 category_id 会变成悬空引用（指向不存在的分类），
     * 导致前端展示分类名时取不到（null），用户体验和数据完整性都受影响。
     * <p>
     * 正确做法：先让管理员把该分类下的 Prompt 迁移到其他分类或删除，再删分类。
     *
     * @param id 要删除的分类 id
     */
    @Override
    public void removeCategory(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("分类不存在");
        }
        // 查该分类下有多少条 Prompt
        Long count = promptMapper.selectCount(
                Wrappers.<Prompt>lambdaQuery().eq(Prompt::getCategoryId, id));
        if (count != null && count > 0) {
            throw new BusinessException("该分类下还有 " + count + " 条Prompt，不能删除");
        }
        // 没有引用了，安全删除
        removeById(id); // DELETE FROM category WHERE id = ?
        // Cache Aside 写路径：删除缓存，保证下次读取拿到删除后的列表
        redis.delete(CACHE_KEY);
    }
}
