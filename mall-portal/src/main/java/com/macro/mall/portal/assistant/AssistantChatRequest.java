package com.macro.mall.portal.assistant;

import java.util.List;

public record AssistantChatRequest(String message, List<AssistantHistoryMessage> history) {
}
