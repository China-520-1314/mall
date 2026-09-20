package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.portal.domain.MemberMessage;

public interface MemberMessageService {
    CommonPage<MemberMessage> list(Integer pageNum, Integer pageSize);

    int unreadCount();

    int markRead(Long id);

    int markAllRead();
}
