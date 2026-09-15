package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.AliPayParam;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

public interface WechatPayService {
    Map<String, Object> nativePay(Long orderId);
    String query(String outTradeNo);
    void notify(HttpServletRequest request) throws Exception;
}
