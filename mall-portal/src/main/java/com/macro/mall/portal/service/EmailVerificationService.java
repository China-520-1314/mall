package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.EmailCodePurpose;

/** 邮箱验证码服务。 */
public interface EmailVerificationService {
    void sendCode(String email, EmailCodePurpose purpose);

    void verifyCode(String email, String code, EmailCodePurpose purpose);
}
