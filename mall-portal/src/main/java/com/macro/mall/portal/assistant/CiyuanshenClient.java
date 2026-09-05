package com.macro.mall.portal.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 词元神 Chat Completions API 的最小客户端。
 */
@Component
public class CiyuanshenClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(CiyuanshenClient.class);

    private final ObjectMapper objectMapper;
    private final AssistantProperties properties;
    private final HttpClient httpClient;

    public CiyuanshenClient(ObjectMapper objectMapper, AssistantProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                // 词元神网关当前对 HTTP/2 协商在部分本地 JDK 环境下不稳定，统一使用 HTTP/1.1。
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * 调用 Chat Completions API 并提取纯文本答复。
     */
    public String complete(String instructions, String input) {
        if (!isConfigured()) {
            throw new AssistantClientException("模型服务未配置");
        }

        String endpoint = normalizeBaseUrl(properties.getBaseUrl()) + "/chat/completions";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", instructions),
                Map.of("role", "user", "content", input)
        ));
        payload.put("stream", false);

        final String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new AssistantClientException("模型请求序列化失败", ex);
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getEffectiveApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
        } catch (IllegalArgumentException ex) {
            throw new AssistantClientException("模型服务地址配置无效", ex);
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String responseBody = response.body();
                if (responseBody != null && responseBody.length() > 300) {
                    responseBody = responseBody.substring(0, 300);
                }
                LOGGER.warn("词元神请求失败，HTTP 状态码：{}，响应摘要：{}",
                        response.statusCode(), responseBody);
                throw new AssistantClientException("模型服务暂时不可用（HTTP " + response.statusCode() + "）");
            }
            String text = extractText(response.body());
            if (text == null || text.isBlank()) {
                LOGGER.warn("词元神返回了空响应");
                throw new AssistantClientException("模型服务返回空响应");
            }
            return text.trim();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssistantClientException("模型请求被中断（" + ex.getClass().getSimpleName() + "）", ex);
        } catch (IOException ex) {
            throw new AssistantClientException(
                    "模型服务连接失败（" + ex.getClass().getSimpleName() + "）",
                    ex
            );
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AssistantClientException("模型服务地址未配置");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 兼容 Responses API 及常见 OpenAI 兼容服务的文本字段。
     */
    private String extractText(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String outputText = textValue(root.get("output_text"));
            if (outputText != null) {
                return outputText;
            }

            JsonNode output = root.get("output");
            String outputContent = extractOutputContent(output);
            if (outputContent != null) {
                return outputContent;
            }

            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    String choiceContent = textValue(message.get("content"));
                    if (choiceContent != null) {
                        return choiceContent;
                    }
                }
            }
            return null;
        } catch (IOException ex) {
            LOGGER.warn("词元神响应不是合法 JSON");
            throw new AssistantClientException("模型服务返回格式异常", ex);
        }
    }

    private String extractOutputContent(JsonNode output) {
        if (output == null || !output.isArray()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode item : output) {
            JsonNode content = item.get("content");
            if (content == null || !content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                String text = textValue(contentItem.get("text"));
                if (text != null && !text.isBlank()) {
                    parts.add(text.trim());
                }
            }
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private String textValue(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    public static class AssistantClientException extends RuntimeException {
        public AssistantClientException(String message) {
            super(message);
        }

        public AssistantClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
