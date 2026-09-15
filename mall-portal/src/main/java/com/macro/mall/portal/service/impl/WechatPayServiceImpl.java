package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.config.WechatPayConfig;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.WechatPayService;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.AutoCertificateNotificationConfig;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WechatPayServiceImpl implements WechatPayService {
    private final WechatPayConfig config;
    private final OmsOrderMapper orderMapper;
    private final OmsPortalOrderService orderService;
    private volatile NativePayService nativePayService;

    public WechatPayServiceImpl(WechatPayConfig config, OmsOrderMapper orderMapper, OmsPortalOrderService orderService) {
        this.config = config; this.orderMapper = orderMapper; this.orderService = orderService;
    }

    private NativePayService client() {
        if (!config.isEnabled() || blank(config.getAppId()) || blank(config.getMchId()) || blank(config.getApiV3Key())
                || blank(config.getSerialNo()) || (blank(config.getPrivateKeyPath()) && blank(config.getPrivateKey())))
            Asserts.fail("微信支付未配置完整商户参数");
        if (nativePayService == null) synchronized (this) {
            if (nativePayService == null) {
                RSAAutoCertificateConfig.Builder b = new RSAAutoCertificateConfig.Builder()
                        .merchantId(config.getMchId()).merchantSerialNumber(config.getSerialNo()).apiV3Key(config.getApiV3Key());
                if (!blank(config.getPrivateKeyPath())) b.privateKeyFromPath(config.getPrivateKeyPath()); else b.privateKey(config.getPrivateKey());
                Config c = b.build();
                nativePayService = new NativePayService.Builder().config(c).build();
            }
        }
        return nativePayService;
    }

    @Override public Map<String, Object> nativePay(Long orderId) {
        OmsOrderDetail order = orderService.detail(orderId);
        if (order == null || order.getStatus() != 0) Asserts.fail("订单不存在或已支付");
        PrepayRequest req = new PrepayRequest(); req.setAppid(config.getAppId()); req.setMchid(config.getMchId());
        req.setDescription("Mall商品订单"); req.setOutTradeNo(order.getOrderSn()); req.setNotifyUrl(config.getNotifyUrl());
        Amount amount = new Amount(); amount.setCurrency("CNY"); amount.setTotal(order.getPayAmount().multiply(BigDecimal.valueOf(100)).intValueExact()); req.setAmount(amount);
        PrepayResponse response = client().prepay(req); Map<String,Object> result = new HashMap<>(); result.put("codeUrl", response.getCodeUrl()); result.put("orderSn", order.getOrderSn()); return result;
    }

    @Override public String query(String outTradeNo) { QueryOrderByOutTradeNoRequest req = new QueryOrderByOutTradeNoRequest(); req.setOutTradeNo(outTradeNo); req.setMchid(config.getMchId()); Transaction t = client().queryOrderByOutTradeNo(req); if (t.getTradeState() == Transaction.TradeStateEnum.SUCCESS) orderService.paySuccessByOrderSn(outTradeNo, 2); return t.getTradeState().name(); }

    @Override public void notify(HttpServletRequest request) throws Exception {
        String body; try (BufferedReader r = request.getReader()) { body = r.lines().collect(Collectors.joining("\n")); }
        RequestParam p = new RequestParam.Builder().serialNumber(request.getHeader("Wechatpay-Serial"))
                .nonce(request.getHeader("Wechatpay-Nonce")).signature(request.getHeader("Wechatpay-Signature"))
                .timestamp(request.getHeader("Wechatpay-Timestamp")).body(body).build();
        AutoCertificateNotificationConfig.Builder nb = new AutoCertificateNotificationConfig.Builder()
                .merchantId(config.getMchId()).merchantSerialNumber(config.getSerialNo()).apiV3Key(config.getApiV3Key());
        if (!blank(config.getPrivateKeyPath())) nb.privateKeyFromPath(config.getPrivateKeyPath()); else nb.privateKey(config.getPrivateKey());
        Transaction t = new NotificationParser(nb.build()).parse(p, Transaction.class);
        if (t.getTradeState() != Transaction.TradeStateEnum.SUCCESS || t.getAmount() == null) return;
        com.macro.mall.model.OmsOrderExample example = new com.macro.mall.model.OmsOrderExample();
        example.createCriteria().andOrderSnEqualTo(t.getOutTradeNo());
        java.util.List<OmsOrder> orders = orderMapper.selectByExample(example);
        if (orders.size() != 1) throw new IllegalStateException("微信回调订单不存在");
        OmsOrder order = orders.get(0);
        int expected = order.getPayAmount().multiply(BigDecimal.valueOf(100)).intValueExact();
        if (!config.getMchId().equals(t.getMchid()) || !config.getAppId().equals(t.getAppid()) || t.getAmount().getTotal() == null || t.getAmount().getTotal() != expected)
            throw new IllegalStateException("微信回调订单金额或商户信息不匹配");
        orderService.paySuccessByOrderSn(t.getOutTradeNo(), 2);
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
