package com.macro.mall.portal.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 词元神模型服务配置。
 *
 * API Key 只允许通过环境变量注入，不写入前端或代码仓库。
 */
@Component
@ConfigurationProperties(prefix = "ciyuanshen")
public class AssistantProperties {
    private String baseUrl = "https://api.ciyuanshen.top/v1";
    private String apiKey = "";
    private String textApiKey = "";
    private String model = "gpt-5.6-terra";
    private int timeoutSeconds = 30;
    private int maxHistory = 8;
    private int maxMessageLength = 800;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getTextApiKey() {
        return textApiKey;
    }

    public void setTextApiKey(String textApiKey) {
        this.textApiKey = textApiKey;
    }

    /**
     * 文字客服优先使用专用 Key，兼容已有的通用词元神 Key 配置。
     */
    public String getEffectiveApiKey() {
        return textApiKey != null && !textApiKey.isBlank() ? textApiKey : apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public boolean isConfigured() {
        String effectiveApiKey = getEffectiveApiKey();
        return effectiveApiKey != null && !effectiveApiKey.isBlank();
    }
}
