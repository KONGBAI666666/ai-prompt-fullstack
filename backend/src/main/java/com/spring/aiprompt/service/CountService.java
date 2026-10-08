package com.spring.aiprompt.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spring.aiprompt.entity.Prompt;
import com.spring.aiprompt.mapper.PromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 浏览数计数服务 —— 高频写场景的 Redis 削峰方案
 * <p>
 * 【演进故事】为什么从"MySQL 原子 UPDATE"升级为"Redis 计数 + 定时落库"：
 * 旧方案（UPDATE prompt SET view_count = view_count + 1）在单机低并发下完全正确，
 * 但每次浏览都是一次磁盘随机写 + 行锁，热门内容下并发 UPDATE 会在同一行上排队。
 * 新方案把高频小写合并成低频批量写：
 * - 写路径：Redis INCR（纯内存、单线程命令、天然原子）
 * - 读路径：数据库基准值 + Redis 未落库增量，用户看到的永远是实时值
 * - 落库：定时任务每 30 秒把增量批量合并回 MySQL，数据库写压力降为原来的 1/N
 * <p>
 * 【key 设计】
 * - prompt:view:delta:{id} —— 该 Prompt 未落库的浏览增量（INCR 累加）
 * - prompt:view:active    —— 有增量的 Prompt id 集合（Set），落库任务只扫它，不做全键 SCAN
 * <p>
 * 【防丢计数设计】落库任务采用"GET → UPDATE → DECRBY 已落库量"三步，
 * 而不是 GET → UPDATE → DEL：
 * 如果 GET 拿到 delta=5 之后、DEL 之前，又有用户浏览使 key 变成 6，
 * DEL 会把没落库的那 1 次也删掉——丢计数。DECRBY 5 只扣走已持久化的部分，
 * 剩余 1 留给下一轮，配合 Set 的 SADD 幂等回补，任何时序下都不丢。
 * （不用 Redis 6.2 的 GETDEL 是因为本机是 3.2 版本，且 GETDEL 同样有上述窗口问题）
 * <p>
 * 【一致性窗口】数据库最多滞后 30 秒，但读路径做了增量合并，对用户无感知；
 * Redis 若宕机，丢失的只是最近 30 秒内的浏览增量，数据库基准值不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountService {

    private final StringRedisTemplate redis;
    private final PromptMapper promptMapper;

    private static final String DELTA_KEY_PREFIX = "prompt:view:delta:";
    private static final String ACTIVE_SET = "prompt:view:active";
    /** 增量 key 兜底过期：正常情况下 30 秒内就会被落库任务消费，2 天 TTL 防孤儿 key 常驻内存 */
    private static final Duration DELTA_TTL = Duration.ofDays(2);

    /**
     * 浏览数 +1（用户点进详情页时调用）
     *
     * @param id            Prompt id
     * @param baseViewCount 数据库里的基准浏览数（调用方刚查出来的实体值）
     * @return 合并后的实时浏览数 = 基准值 + Redis 增量（含本次 +1）
     */
    public long incrView(Long id, long baseViewCount) {
        String deltaKey = DELTA_KEY_PREFIX + id;
        // INCR：Redis 单线程执行命令，天然原子，100 个并发浏览 = 精确 +100
        Long delta = redis.opsForValue().increment(deltaKey);
        // 登记活跃 id：落库任务的"待办清单"。SADD 幂等，重复加没副作用
        redis.opsForSet().add(ACTIVE_SET, id.toString());
        // 兜底 TTL：即使落库任务长期挂掉，增量也不会永久占用内存
        redis.expire(deltaKey, DELTA_TTL);
        return baseViewCount + (delta == null ? 0 : delta);
    }

    /**
     * 批量查询未落库增量（列表页合并展示用）
     * <p>
     * 列表数据来自数据库（可能落后最多 30 秒），把 Redis 增量加回去，
     * 保证列表页和详情页看到的浏览数一致。
     * 用 MGET 一次网络往返取回所有 key，而不是循环 GET。
     *
     * @param ids Prompt id 集合
     * @return Map&lt;id, 增量&gt;，只包含增量 &gt; 0 的条目
     */
    public Map<Long, Long> getViewDeltas(Collection<Long> ids) {
        Map<Long, Long> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<String> keys = ids.stream().map(id -> DELTA_KEY_PREFIX + id).toList();
        List<String> values = redis.opsForValue().multiGet(keys);
        int i = 0;
        for (Long id : ids) {
            String v = values == null ? null : values.get(i++);
            if (v != null) {
                result.put(id, Long.parseLong(v));
            }
        }
        return result;
    }

    /**
     * 定时落库：把 Redis 增量合并回 MySQL（每 30 秒，上一轮跑完才开始计时）
     * <p>
     * fixedDelay 而非 fixedRate：避免上一轮处理慢时任务堆积。
     * @Scheduled 默认单线程调度，本方法不会并发重入，下面的三步时序才成立。
     */
    @Scheduled(fixedDelay = 30_000)
    public void persistViewDeltas() {
        Set<String> activeIds = redis.opsForSet().members(ACTIVE_SET);
        if (activeIds == null || activeIds.isEmpty()) {
            return;
        }
        for (String idStr : activeIds) {
            try {
                persistOne(idStr);
            } catch (Exception e) {
                // 单条失败不中断整轮：这个 id 留在 Set 里，下一轮重试
                log.warn("浏览数落库失败 id={}，下轮重试: {}", idStr, e.getMessage());
            }
        }
    }

    /**
     * 单个 Prompt 的增量落库（三步防丢设计，见类注释）
     */
    private void persistOne(String idStr) {
        String deltaKey = DELTA_KEY_PREFIX + idStr;
        String v = redis.opsForValue().get(deltaKey);
        // key 已被 TTL 清掉或刚好落库完：直接从待办清单移除
        if (v == null) {
            redis.opsForSet().remove(ACTIVE_SET, idStr);
            return;
        }
        long delta = Long.parseLong(v);
        if (delta <= 0) {
            redis.opsForSet().remove(ACTIVE_SET, idStr);
            return;
        }
        // 第 1 步：把增量合并进数据库（原子 SQL，不依赖读到的旧值）
        promptMapper.update(null, Wrappers.<Prompt>lambdaUpdate()
                .eq(Prompt::getId, Long.parseLong(idStr))
                .setSql("view_count = view_count + " + delta));
        // 第 2 步：只扣走已落库的部分（DECRBY）。GET 之后若有新浏览进来，剩余量留给下一轮
        Long remain = redis.opsForValue().decrement(deltaKey, delta);
        // 第 3 步：扣完归零（或刚好没有新浏览）才清理 key 和待办登记
        if (remain != null && remain <= 0) {
            redis.delete(deltaKey);
            redis.opsForSet().remove(ACTIVE_SET, idStr);
        }
        // remain > 0：GET 之后又有新浏览，id 仍在 Set 里，下一轮继续处理
    }
}
