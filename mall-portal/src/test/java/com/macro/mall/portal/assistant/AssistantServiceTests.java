package com.macro.mall.portal.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智能客服的无网络安全兜底测试。
 */
class AssistantServiceTests {

    @Test
    void returnsFallbackWhenApiKeyIsMissing() {
        AssistantProperties properties = new AssistantProperties();
        properties.setApiKey("");
        AssistantService service = new AssistantService(
                new CiyuanshenClient(new ObjectMapper(), properties),
                properties
        );

        AssistantChatResponse response = service.chat(new AssistantChatRequest("如何申请退货？", null));

        assertTrue(response.fallback());
        assertTrue(response.reply().contains("尚未配置"));
    }

    @Test
    void rejectsBlankMessage() {
        AssistantProperties properties = new AssistantProperties();
        AssistantService service = new AssistantService(
                new CiyuanshenClient(new ObjectMapper(), properties),
                properties
        );

        assertThrows(IllegalArgumentException.class, () -> service.chat(new AssistantChatRequest(" ", null)));
    }
}
