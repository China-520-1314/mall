package com.macro.mall.portal.domain;

/** 邮件发送结果，不向客户端返回验证码。 */
public record EmailCodeSendResult(String email, long cooldownSeconds, long expiresIn) {
}
