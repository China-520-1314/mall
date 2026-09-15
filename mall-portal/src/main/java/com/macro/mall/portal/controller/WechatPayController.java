package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.WechatPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/wechatpay")
@Tag(name = "WechatPayController", description = "微信支付相关接口")
public class WechatPayController {
    private final WechatPayService service;
    public WechatPayController(WechatPayService service) { this.service = service; }

    @PostMapping("/native")
    @Operation(summary = "创建微信Native扫码支付订单")
    public CommonResult<Map<String, Object>> nativePay(@RequestParam Long orderId) { return CommonResult.success(service.nativePay(orderId)); }

    @GetMapping("/query")
    @Operation(summary = "查询微信支付状态")
    public CommonResult<String> query(@RequestParam String outTradeNo) { return CommonResult.success(service.query(outTradeNo)); }

    @PostMapping("/notify")
    @Operation(summary = "微信支付回调")
    public Map<String, String> notify(HttpServletRequest request) throws Exception { service.notify(request); return Map.of("code", "SUCCESS", "message", "成功"); }
}
