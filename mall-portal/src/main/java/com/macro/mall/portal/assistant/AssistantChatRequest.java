package com.macro.mall.portal.assistant;

import java.util.List;

/**
 * 智能客服请求参数。
 */
public record AssistantChatRequest(String message, List<AssistantHistoryMessage> history) {
}
