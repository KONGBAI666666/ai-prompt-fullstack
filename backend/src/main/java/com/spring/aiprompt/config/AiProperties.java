package com.spring.aiprompt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 在线试运行配置 —— 绑定 application-dev.yml 里的 ai.* 配置段
 * <p>
 * 协议选型：OpenAI 兼容协议（/v1/chat/completions + stream:true）。
 * DeepSeek、通义千问、智谱、Kimi、OpenAI 官方都支持这套协议，
 * 换供应商只改 base-url / api-key / model 三个配置，代码零改动。
 * <p>
 * 申请 DeepSeek key：https://platform.deepseek.com → API Keys
 * 几块钱额度足够个人项目演示很久。填好后把 ai.enabled 改为 true。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 总开关：false 时 /ai/run 返回友好提示，避免未配置 key 时报一堆连接错误 */
    private boolean enabled = false;

    /** OpenAI 兼容服务的基地址（不含 /v1/chat/completions 路径） */
    private String baseUrl = "https://api.deepseek.com";

    /** API Key（配置在 application-dev.yml，该文件不入 git，不会泄露） */
    private String apiKey = "";

    /** 模型名：deepseek-chat / qwen-plus / glm-4-flash 等 */
    private String model = "deepseek-chat";

    /** 每用户每天可试运行次数（Redis 计数限流，防刷保护 API 费用） */
    private int dailyLimit = 20;

    /** 单次生成超时秒数 */
    private int timeoutSeconds = 60;
}
