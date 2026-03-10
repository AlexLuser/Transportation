package com.fm.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型配置属性
 *
 * 支持任何兼容 OpenAI Chat Completions API 格式的模型，包括：
 *   - OpenAI GPT 系列
 *   - 阿里通义千问（Qwen）
 *   - 百度文心一言（ERNIE）
 *   - 本地部署 Ollama（llama3、qwen2.5 等）
 *   - DeepSeek、Kimi 等
 *
 * 切换模型只需修改 application.yml，代码无需改动。
 */
@Component
@ConfigurationProperties(prefix = "llm")
public class LLMProperties {

    /** 是否启用大模型（false 时自动降级到规则计算） */
    private boolean enabled = false;

    /** API 基础地址，例如 https://api.openai.com 或 http://localhost:11434 */
    private String baseUrl = "https://api.openai.com";

    /** API 密钥（Ollama 本地部署可留空） */
    private String apiKey = "";

    /** 模型名称，例如 gpt-4o-mini / qwen-turbo / deepseek-chat / llama3 */
    private String model = "gpt-4o-mini";

    /** 请求超时（秒） */
    private int timeoutSeconds = 30;

    /** 最大 token 数（控制输出长度） */
    private int maxTokens = 1024;

    /** 温度参数（0.0~1.0，越低越确定性，路线分析建议 0.2~0.4） */
    private double temperature = 0.3;

    /**
     * 是否启用 response_format=json_object（要求模型严格输出 JSON）
     * 支持：OpenAI GPT-4o、DeepSeek-V3、Qwen-Plus 等
     * 不支持：部分 Ollama 本地模型（设为 false，靠 system prompt 约束输出）
     */
    private boolean jsonModeEnabled = true;

    /** GPS 上报多少次触发一次 LLM ETA 重算（避免频繁调用） */
    private int etaUpdateInterval = 10;

    // -------- Getter / Setter --------

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public boolean isJsonModeEnabled() { return jsonModeEnabled; }
    public void setJsonModeEnabled(boolean jsonModeEnabled) { this.jsonModeEnabled = jsonModeEnabled; }

    public int getEtaUpdateInterval() { return etaUpdateInterval; }
    public void setEtaUpdateInterval(int etaUpdateInterval) { this.etaUpdateInterval = etaUpdateInterval; }
}

