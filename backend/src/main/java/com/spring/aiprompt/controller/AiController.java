package com.spring.aiprompt.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.spring.aiprompt.config.AiProperties;
import com.spring.aiprompt.dto.AiRunDTO;
import com.spring.aiprompt.entity.Prompt;
import com.spring.aiprompt.exception.BusinessException;
import com.spring.aiprompt.service.AiService;
import com.spring.aiprompt.service.PromptHistoryService;
import com.spring.aiprompt.service.PromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDate;

/**
 * AI 在线试运行 Controller —— SSE 流式接口
 * <p>
 * 路径：POST /ai/run
 * 功能：选中一条提示词 → 填补充输入 → 大模型按提示词生成 → 打字机效果流式返回。
 * <p>
 * 【为什么用 SSE 而不是普通 JSON 接口】大模型生成一段回答要几秒到几十秒，
 * 普通 JSON 接口会让用户盯着空白页面干等；SSE 把生成过程切成小文本块实时推送，
 * 用户看到的是"打字机在打字"，体感延迟从"十几秒"降到"一秒内出字"。
 * <p>
 * 【为什么前端不用 EventSource】浏览器原生 EventSource 只支持 GET、
 * 不能带自定义 Authorization 请求头，所以前端用 fetch + ReadableStream 手动解析流。
 * <p>
 * 【安全设计】
 * - 权限点 ai:run：RBAC 可控（管理员可在后台随时关掉某角色的试运行能力）
 * - 提示词正文按 promptId 从库里取：前端传什么内容都不算数，防止把任意文本
 *   塞给大模型白嫖接口（也是"不信任前端"原则的又一次落地）
 * - Redis 每日限流：INCR + EXPIRE 原子计数，超限直接 429，保护 API 费用不被刷爆
 * <p>
 * 【虚拟线程】生成过程阻塞式读上游流，放虚拟线程里跑（JDK 21）。
 * 相比平台线程：虚拟线程阻塞不占用 OS 线程，几百个并发生成也扛得住；
 * 相比异步回调风格：代码仍是同步写法，可读性好、异常栈完整。
 */
@Slf4j
@Tag(name = "AI试运行")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiProperties aiProps;
    private final PromptService promptService;
    private final PromptHistoryService promptHistoryService;
    private final StringRedisTemplate redis;

    /** 限流 key 前缀：ai:quota:{userId}:{yyyyMMdd} */
    private static final String QUOTA_KEY_PREFIX = "ai:quota:";
    /** 限流 key 兜底 TTL：2 天（key 带日期，天然按自然日分桶，过期自动清理） */
    private static final Duration QUOTA_TTL = Duration.ofDays(2);

    /**
     * 试运行一条提示词（SSE 流式响应）
     * <p>
     * 响应是一串 text/event-stream 事件，每个事件的 data 是单行 JSON：
     * {"t":"c","v":"增量文本"} —— 一小段生成内容（前端追加渲染）
     * {"t":"done"}             —— 正常结束
     * {"t":"error","v":"原因"} —— 出错
     */
    @Operation(summary = "AI试运行（SSE流式）")
    @SaCheckPermission("ai:run")
    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@Validated @RequestBody AiRunDTO dto) {
        // —— 0. 功能开关 ——
        if (!aiProps.isEnabled()) {
            throw new BusinessException("AI试运行功能暂未开放（管理员未配置 API Key）");
        }

        // —— 1. 每日限流（先于取内容，超限的请求连数据库都不查） ——
        long userId = StpUtil.getLoginIdAsLong();
        String quotaKey = QUOTA_KEY_PREFIX + userId + ":" + LocalDate.now();
        Long used = redis.opsForValue().increment(quotaKey);
        if (used != null && used == 1L) {
            // 第一次使用：设置兜底 TTL，防止 key 永不过期
            redis.expire(quotaKey, QUOTA_TTL);
        }
        if (used != null && used > aiProps.getDailyLimit()) {
            throw new BusinessException(429, "今日试运行次数已用完（"
                    + aiProps.getDailyLimit() + " 次/天），明天再来吧");
        }

        // —— 2. 按 id 取提示词正文（不信任前端传内容） ——
        Prompt prompt = promptService.getById(dto.getPromptId());
        if (prompt == null) {
            throw new BusinessException("Prompt不存在");
        }

        // —— 3. 建 SSE 连接，生成过程放虚拟线程 ——
        SseEmitter emitter = new SseEmitter(Duration.ofSeconds(aiProps.getTimeoutSeconds() + 10).toMillis());
        Thread.ofVirtual().name("ai-run-", userId).start(() -> {
            boolean ok = aiService.run(prompt.getContent(), dto.getInput(), emitter);
            // 生成成功 → 记一条使用历史（复用"使用(M:N)"联系，详情页的使用记录、
            // 管理端的统计都自动包含 AI 试运行）
            if (ok) {
                try {
                    promptHistoryService.record(prompt.getId());
                } catch (Exception e) {
                    // 历史记录失败不影响生成结果本身
                    log.warn("AI试运行的使用历史记录失败 promptId={}: {}", prompt.getId(), e.getMessage());
                }
            }
        });
        // 立即返回 emitter：Tomcat 线程马上释放去服务别的请求，生成在虚拟线程里继续推
        return emitter;
    }
}
