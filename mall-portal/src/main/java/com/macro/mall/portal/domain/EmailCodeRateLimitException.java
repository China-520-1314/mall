package com.macro.mall.portal.domain;

import com.macro.mall.common.exception.ApiException;

/** 让客户端按服务器实际剩余时间恢复倒计时。 */
public class EmailCodeRateLimitException extends ApiException {
    private final long retryAfterSeconds;

    public EmailCodeRateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
