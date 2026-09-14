package com.macro.mall.portal.assistant;

import java.util.List;

/** 智能客服响应，动作路径由服务端固定。 */
public record AssistantChatResponse(String reply, boolean fallback, List<AssistantAction> actions) {
    public AssistantChatResponse(String reply, boolean fallback) {
        this(reply, fallback, List.of());
    }
}
