package com.macro.mall.portal.assistant;

/** 客服可提供的受控页面动作，路径由后端固定。 */
public record AssistantAction(String label, String route, boolean requiresLogin) {
}
