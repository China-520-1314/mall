package com.macro.mall.service;

import com.macro.mall.dto.UmsMemberMessageParam;

/** 站内信服务。 */
public interface UmsMemberMessageService {
    int sendToOrderMember(Long orderId, UmsMemberMessageParam param);
}
