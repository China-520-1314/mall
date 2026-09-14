package com.macro.mall.portal.assistant;

import java.util.List;

/** 当前登录用户的最小业务摘要，不包含收货地址、手机号等敏感信息。 */
public record AssistantBusinessSummary(String type, int total, List<Item> items) {
    public record Item(Long id, String number, String status, String time, String logistics) {
    }
}
