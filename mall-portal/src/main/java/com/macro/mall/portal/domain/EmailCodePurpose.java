package com.macro.mall.portal.domain;

/** 邮箱验证码用途，防止不同业务间复用验证码。 */
public enum EmailCodePurpose {
    REGISTER,
    RESET_PASSWORD,
    CHANGE_PASSWORD
}
