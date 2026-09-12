package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.EmailCodePurpose;
import com.macro.mall.portal.domain.EmailCodeSendResult;

/** 邮箱验证码服务。 */
public interface EmailVerificationService {
    EmailCodeSendResult sendCode(String email, EmailCodePurpose purpose);

    void verifyCode(String email, String code, EmailCodePurpose purpose);
}
