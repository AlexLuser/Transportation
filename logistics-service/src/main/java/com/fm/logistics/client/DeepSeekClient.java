package com.fm.logistics.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fm.logistics.dto.LlmDecisionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * DeepSeek API HTTP 客户端
 * DeepSeek 完全兼容 OpenAI Chat Completions 格式，使用 RestTemplate 直接调用
 *
 * 使用前请在 application.yml 中配置：
 *   deepseek.api-key: 你的API密钥（从 platform.deepseek.com 获取）
 *   deepseek.read-timeout-ms: 读取超时（费用校准等大输出常需 30～90s，默认 180000）
 */
@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    @Value("${deepseek.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${deepseek.api-key:your-api-key-here}")
    private String apiKey;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    @Autowired
    public DeepSeekClient(
            ObjectMapper objectMapper,
            @Value("${deepseek.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${deepseek.read-timeout-ms:180000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        factory.setReadTimeout(Math.max(5000, readTimeoutMs));
        this.restTemplate = new RestTemplate(factory);
        log.info("[DeepSeek] HTTP connectTimeout={}ms readTimeout={}ms", connectTimeoutMs, readTimeoutMs);
    }

    /**
     * 调用 DeepSeek Chat API
     * 要求以 JSON 格式返回（通过 response_format 参数强制）
     *
     * @param systemPrompt 系统提示词（定义角色和规则）
     * @param userPrompt   用户提示词（包含具体数据和任务）
     * @return LLM 返回的 JSON 字符串
     */
    public String callApi(String systemPrompt, String userPrompt) throws Exception {
        return callApi(systemPrompt, userPrompt, 800, true);
    }

    /**
     * @param maxTokens    输出上限；边费用校准等长 JSON 需更大（如 4096），避免截断后解析失败
     * @param jsonObjectMode 为 true 时等价原行为，要求模型返回 JSON 对象根节点
     */
    public String callApi(String systemPrompt, String userPrompt, int maxTokens, boolean jsonObjectMode) throws Exception {
        return callApi(systemPrompt, userPrompt, maxTokens, jsonObjectMode, 0.1);
    }

    /**
     * @param temperature 采样温度；结构化数值任务宜偏低（如 0.1），解读类可略高（如 0.45～0.6）
     */
    public String callApi(String systemPrompt, String userPrompt, int maxTokens, boolean jsonObjectMode,
                          double temperature) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", Math.max(0.0, Math.min(2.0, temperature)));
        requestBody.put("max_tokens", Math.max(256, maxTokens));

        if (jsonObjectMode) {
            Map<String, String> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_object");
            requestBody.put("response_format", responseFormat);
        }

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        requestBody.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.info("[DeepSeek] 发送请求 model={}, promptLen={}, maxTokens={}", model, userPrompt.length(), maxTokens);

        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        if (response.getBody() == null) {
            throw new RuntimeException("DeepSeek API 返回空响应体");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("DeepSeek API 响应中 choices 为空");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");

        log.info("[DeepSeek] 响应成功，内容长度={}", content != null ? content.length() : 0);
        log.debug("[DeepSeek] 响应内容: {}", content);
        return content;
    }

    /**
     * 统一取出助手正文：{@link #callApi} 已返回 {@code message.content}；
     * 若传入整段 Chat Completions JSON（含 {@code choices}），也会解包出 content。
     * <p>排查解析失败：将 {@code com.fm.logistics.client.DeepSeekClient} 设为 DEBUG，查看上方法打印的完整 content。</p>
     */
    public String unwrapAssistantContent(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode first = choices.get(0);
                if (first != null) {
                    String inner = first.path("message").path("content").asText("");
                    if (!inner.isBlank()) {
                        return inner;
                    }
                }
            }
        } catch (JsonProcessingException e) {
            return trimmed;
        }
        return trimmed;
    }

    /**
     * 解析 LLM 返回的 JSON 字符串为 LlmDecisionResult
     * 使用 @JsonIgnoreProperties(ignoreUnknown=true)，LLM 多余字段不影响解析
     */
    public LlmDecisionResult parseDecisionResult(String json) throws Exception {
        return objectMapper.readValue(json, LlmDecisionResult.class);
    }
}
