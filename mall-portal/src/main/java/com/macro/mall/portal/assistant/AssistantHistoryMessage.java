package com.macro.mall.portal.assistant;

/**
 * 客服上下文中的一条历史消息。
 */
public record AssistantHistoryMessage(String role, String content) {
}
