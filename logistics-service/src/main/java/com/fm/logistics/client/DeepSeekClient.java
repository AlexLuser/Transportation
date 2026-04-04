package com.fm.logistics.client;

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

    // RestTemplate 在构造时创建，避免使用 @PostConstruct
    private final RestTemplate restTemplate;

    @Autowired
    public DeepSeekClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
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
        // 构建请求体（兼容 OpenAI 格式）
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.1);   // 低温度保证输出稳定可解析
        requestBody.put("max_tokens", 800);

        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        requestBody.put("response_format", responseFormat);

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

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.info("[DeepSeek] 发送请求 model={}, promptLen={}", model, userPrompt.length());

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
     * 解析 LLM 返回的 JSON 字符串为 LlmDecisionResult
     * 使用 @JsonIgnoreProperties(ignoreUnknown=true)，LLM 多余字段不影响解析
     */
    public LlmDecisionResult parseDecisionResult(String json) throws Exception {
        return objectMapper.readValue(json, LlmDecisionResult.class);
    }
}
