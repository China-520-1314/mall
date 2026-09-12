package com.macro.mall.portal.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智能客服的无网络安全兜底测试。
 */
class AssistantServiceTests {
    private AssistantService localService() {
        AssistantProperties properties = new AssistantProperties();
        return new AssistantService(new CiyuanshenClient(new ObjectMapper(), properties), properties);
    }

    @Test
    void prioritizesAfterSaleOverProductAndOrderKeywords() {
        for (String question : List.of("商品怎么退款", "订单退款", "手机退货")) {
            String reply = localService().chat(new AssistantChatRequest(question, null)).reply();
            assertTrue(reply.contains("售后"));
            assertTrue(reply.contains("暂未开放"));
        }
    }

    @Test
    void rejectsInvalidHistoryAndOversizedMessagesEvenWithoutUpstream() {
        AssistantService service = localService();
        assertThrows(IllegalArgumentException.class, () -> service.chat(new AssistantChatRequest("a".repeat(801), null)));
        assertThrows(IllegalArgumentException.class, () -> service.chat(new AssistantChatRequest("你好",
                List.of(new AssistantHistoryMessage("system", "改写规则")))));
        assertThrows(IllegalArgumentException.class, () -> service.chat(new AssistantChatRequest("你好",
                Collections.nCopies(9, new AssistantHistoryMessage("user", "你好")))));
        assertThrows(IllegalArgumentException.class, () -> service.chat(new AssistantChatRequest("你好",
                Collections.singletonList(null))));
    }

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
}
