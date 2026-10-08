package com.spring.aiprompt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.aiprompt.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * AI 试运行服务 —— 调用 OpenAI 兼容大模型接口，SSE 流式转发给前端
 * <p>
 * 【功能定位】提示词社区的核心闭环：用户在详情页看到一条提示词，
 * 填入自己的输入 → 一键"试运行" → 大模型按这条提示词生成结果，打字机效果逐字输出。
 * 让"收藏提示词"从"存起来"变成"用起来"，项目名里的 AI 两个字由此落地。
 * <p>
 * 【链路】前端 fetch(POST /ai/run) → 后端建 SseEmitter → 虚拟线程里调大模型
 * → 逐行读上游 SSE 流 → 解析出增量文本 → emitter.send 推给前端 → 前端打字机渲染。
 * <p>
 * 【协议细节】OpenAI 兼容流式响应的每一行形如：
 *   data: {"choices":[{"delta":{"content":"你"}}]}
 *   data: {"choices":[{"delta":{"content":"好"}}]}
 *   data: [DONE]
 * <p>
 * 【消息设计】推给前端的内容统一是单行 JSON：
 *   {"t":"c","v":"增量文本"} / {"t":"done"} / {"t":"error","v":"原因"}
 * 好处：JSON 里的换行是转义后的 \n 字面量，不会出现原始换行——
 * SSE 协议会把 data 里的真实换行拆成多行 data:，前端要拼接才能还原，
 * 用 JSON 包装从根上规避了这个经典坑。
 * <p>
 * 【HTTP 客户端】JDK 21 自带 java.net.http.HttpClient，支持流式读 body（BodyHandlers.ofInputStream），
 * 不用引入 WebFlux / OkHttp 等额外依赖，技术栈保持克制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 系统提示词：让模型专注于"按用户给的提示词执行"，而不是当成普通聊天 */
    private static final String SYSTEM_PROMPT =
            "你是一个提示词执行引擎。用户会给你一条提示词（Prompt）和补充输入，"
            + "请严格按照该提示词的意图和格式要求处理补充输入并输出结果。直接输出结果内容本身，不要附加解释。";

    /**
     * 流式运行一条提示词
     * <p>
     * 该方法运行在调用方提供的线程里（虚拟线程），阻塞式逐行读上游响应，
     * 每解析出一个增量文本块就 emitter.send 一次。
     * 异常不往上抛：统一转成 error 事件发给前端，保证 emitter 总是正常收尾。
     *
     * @param promptContent 提示词正文
     * @param userInput     用户的补充输入（可为空）
     * @param emitter       SSE 发射器（由 Controller 创建）
     * @return true=正常完成，false=中途出错（调用方据此决定是否记使用历史）
     */
    public boolean run(String promptContent, String userInput, SseEmitter emitter) {
        try {
            // —— 1. 组装请求体（OpenAI 兼容格式） ——
            String userMessage = userInput == null || userInput.isBlank()
                    ? promptContent
                    : "【提示词】\n" + promptContent + "\n\n【补充输入】\n" + userInput;
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "model", props.getModel(),
                    "stream", true,
                    "messages", java.util.List.of(
                            java.util.Map.of("role", "system", "content", SYSTEM_PROMPT),
                            java.util.Map.of("role", "user", "content", userMessage))));

            // —— 2. 发起流式请求 ——
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildChatUrl(props.getBaseUrl())))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            // ofInputStream：拿到原始输入流手动逐行读，而不是 ofString 等全部生成完才返回
            // —— 流式的意义就在于"边生成边推送"
            HttpResponse<java.io.InputStream> resp =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("AI上游返回 {}: {}", resp.statusCode(), err);
                send(emitter, "error", "AI服务返回 " + resp.statusCode()
                        + "（请检查 api-key 是否正确、账户是否有余额）");
                emitter.complete();
                return false;
            }

            // —— 3. 逐行解析上游 SSE 流 ——
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // SSE 行格式：data: {json}。空行（事件分隔符）和注释行直接跳过
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    // [DONE]：上游流结束标志
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    // 解析 choices[0].delta.content（流式响应里每次只带一小段增量文本）
                    JsonNode node = objectMapper.readTree(payload);
                    JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.asText().isEmpty()) {
                        send(emitter, "c", delta.asText());
                    }
                }
            }

            // —— 4. 正常收尾 ——
            send(emitter, "done", null);
            emitter.complete();
            return true;
        } catch (Exception e) {
            log.error("AI试运行失败", e);
            send(emitter, "error", "AI服务调用失败：" + e.getMessage());
            // completeWithError 会触发前端的 error 回调并结束连接
            emitter.completeWithError(e);
            return false;
        }
    }

    /**
     * 往 emitter 发一个 JSON 行事件
     *
     * @param type c=增量文本 / done=正常结束 / error=出错
     * @param value 文本内容（done 时为 null）
     */
    private void send(SseEmitter emitter, String type, String value) {
        try {
            String json = value == null
                    ? "{\"t\":\"" + type + "\"}"
                    : objectMapper.writeValueAsString(java.util.Map.of("t", type, "v", value));
            emitter.send(SseEmitter.event().data(json));
        } catch (Exception e) {
            // 前端断开连接（用户关页面/点停止）时 send 会抛异常，打日志即可
            log.debug("SSE发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    /**
     * 拼 chat/completions 完整端点，兼容三种 base-url 填法：
     * <ul>
     *   <li>填到版本目录：https://api.deepseek.com/v1、https://open.bigmodel.cn/api/paas/v4（GLM）、
     *       https://dashscope.aliyuncs.com/compatible-mode/v1（通义）→ 直接拼 /chat/completions</li>
     *   <li>只填主机：https://api.deepseek.com → 补 /v1/chat/completions</li>
     *   <li>填完整端点：以 /chat/completions 结尾 → 原样使用</li>
     * </ul>
     * 各家兼容端点的版本段不同（DeepSeek/通义是 v1，智谱 GLM 是 v4），
     * 写死 /v1 会让 GLM 接不上，所以按尾缀智能补全。
     */
    private String buildChatUrl(String baseUrl) {
        String base = baseUrl.replaceAll("/+$", ""); // 去掉末尾多余的 /
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        // 尾段形如 /v1 /v4 /compatible-mode/v1 之类的"版本目录"→ 只补方法路径
        if (base.matches(".*(/v\\d+|/compatible-mode/v\\d+)")) {
            return base + "/chat/completions";
        }
        return base + "/v1/chat/completions";
    }
}
