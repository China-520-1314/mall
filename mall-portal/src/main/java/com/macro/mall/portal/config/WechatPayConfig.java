package com.macro.mall.portal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wechat-pay")
public class WechatPayConfig {
    private boolean enabled;
    private String appId;
    private String mchId;
    private String serialNo;
    private String privateKeyPath;
    private String privateKey;
    private String apiV3Key;
    private String notifyUrl;
    private String publicKeyId;
    private String publicKeyPath;
}
