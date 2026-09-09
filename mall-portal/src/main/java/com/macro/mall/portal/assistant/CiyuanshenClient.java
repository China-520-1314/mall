package com.macro.mall.portal.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CiyuanshenClient {
    private final ObjectMapper objectMapper;
    private final AssistantProperties properties;
    private final HttpClient httpClient;

    public CiyuanshenClient(ObjectMapper objectMapper, AssistantProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String complete(String instructions, String input) {
        String endpoint = normalizeBaseUrl(properties.getBaseUrl()) + "/chat/completions";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", instructions),
                Map.of("role", "user", "content", input)
        ));
        payload.put("stream", false);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getEffectiveApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AssistantClientException("模型服务暂时不可用");
            }
            String text = extractText(response.body());
            if (text == null || text.isBlank()) {
                throw new AssistantClientException("模型服务返回空响应");
            }
            return text.trim();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssistantClientException("模型请求被中断", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new AssistantClientException("模型服务连接失败", ex);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AssistantClientException("模型服务地址未配置");
        }
        return baseUrl.trim().replaceAll("/+$", "");
    }

    private String extractText(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isTextual()) {
                return content.asText();
            }
        }
        JsonNode outputText = root.get("output_text");
        return outputText != null && outputText.isTextual() ? outputText.asText() : null;
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
