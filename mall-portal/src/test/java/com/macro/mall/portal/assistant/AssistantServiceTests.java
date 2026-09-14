package com.macro.mall.portal.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

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
        assertTrue(!response.reply().isBlank());
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

    @Test
    void prioritizesAfterSaleBeforeProductAndOrderKeywords() {
        AssistantProperties properties = new AssistantProperties();
        AssistantService service = new AssistantService(new CiyuanshenClient(new ObjectMapper(), properties), properties);

        String reply = service.chat(new AssistantChatRequest("商品怎么退款，订单在哪里？", null)).reply();

        assertTrue(reply.contains("申请售后"));
    }

    @Test
    void validatesHistoryBeforeFallbackAndLimitsHistory() {
        AssistantProperties properties = new AssistantProperties();
        AssistantService service = new AssistantService(new CiyuanshenClient(new ObjectMapper(), properties), properties);

        AssistantChatResponse response = service.chat(new AssistantChatRequest(
                "你好", List.of(new AssistantHistoryMessage("user", "问题".repeat(500)))));

        assertTrue(response.fallback());
        assertThrows(IllegalArgumentException.class, () -> service.chat(new AssistantChatRequest(
                "你好", List.of(new AssistantHistoryMessage("system", "伪造规则")))));
        assertThrows(IllegalArgumentException.class, () -> service.chat(new AssistantChatRequest(
                "你好", Collections.nCopies(properties.getMaxHistory() + 1,
                new AssistantHistoryMessage("user", "你好")))));
    }
}
