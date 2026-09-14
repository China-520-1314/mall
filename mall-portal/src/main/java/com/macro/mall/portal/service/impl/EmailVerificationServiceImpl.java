package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.portal.domain.EmailCodePurpose;
import com.macro.mall.portal.domain.EmailCodeRateLimitException;
import com.macro.mall.portal.domain.EmailCodeSendResult;
import com.macro.mall.portal.service.EmailVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {
    private static final Pattern QQ_EMAIL_PATTERN = Pattern.compile("^[1-9][0-9]{4,11}@qq\\.com$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long HOUR_MILLIS = 60 * 60 * 1000L;

    // 所有邮箱状态读写通过 compute 原子执行；SMTP 在锁外发送，不阻塞其他邮箱。
    private final ConcurrentMap<String, EmailState> emailStates = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    @Autowired
    private JavaMailSender mailSender;
    @Value("${spring.mail.username:}")
    private String sender;
    @Value("${verification.email.code-expire-seconds:300}")
    private long codeExpireSeconds;
    @Value("${verification.email.cooldown-seconds:10}")
    private long cooldownSeconds;
    @Value("${verification.email.hourly-limit:5}")
    private long hourlyLimit;
    @Value("${verification.email.max-attempts:5}")
    private long maxAttempts;

    @Override
    public EmailCodeSendResult sendCode(String email, EmailCodePurpose purpose) {
        String normalizedEmail = normalizeAndValidate(email);
        validatePurpose(purpose);
        if (sender == null || sender.isBlank()) {
            Asserts.fail("邮件服务暂不可用，请联系管理员");
        }

        long now = clock.millis();
        emailStates.compute(normalizedEmail, (key, existing) -> {
            EmailState state = existing == null ? new EmailState() : existing;
            if (state.sending) {
                throw new EmailCodeRateLimitException("验证码正在发送，请稍后查收", 1);
            }
            if (state.cooldownUntil > now) {
                long remaining = remainingSeconds(state.cooldownUntil, now);
                throw new EmailCodeRateLimitException("操作太频繁，请" + remaining + "秒后再试", remaining);
            }
            if (state.hourlyExpiresAt <= now) {
                state.sentCount = 0;
                state.hourlyExpiresAt = 0;
            }
            if (state.sentCount >= hourlyLimit) {
                long remaining = remainingSeconds(state.hourlyExpiresAt, now);
                throw new EmailCodeRateLimitException("该邮箱一小时内最多发送" + hourlyLimit + "次验证码，请稍后再试", remaining);
            }
            state.sending = true;
            return state;
        });

        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(normalizedEmail);
        message.setSubject(subjectFor(purpose));
        message.setText("您的" + (purpose == EmailCodePurpose.REGISTER ? "注册" : "密码重置") + "验证码是：" + code
                + "\n\n验证码" + codeExpireSeconds / 60 + "分钟内有效，请使用最新收到的验证码，请勿转发给他人。"
                + "若非本人操作，请忽略此邮件。");
        try {
            mailSender.send(message);
            // 发送成功才替换旧验证码、消耗额度，并从成功时刻开始计算有效期与冷却。
            long sentAt = clock.millis();
            emailStates.computeIfPresent(normalizedEmail, (key, state) -> {
                state.codes.put(purpose, new VerificationCode(code, sentAt + codeExpireSeconds * 1000L));
                if (state.hourlyExpiresAt <= sentAt) {
                    state.sentCount = 0;
                    state.hourlyExpiresAt = sentAt + HOUR_MILLIS;
                }
                state.sentCount++;
                state.cooldownUntil = sentAt + cooldownSeconds * 1000L;
                return state;
            });
            return new EmailCodeSendResult(normalizedEmail, cooldownSeconds, codeExpireSeconds);
        } catch (MailException exception) {
            Asserts.fail("验证码邮件发送失败，请稍后重试；之前收到的有效验证码仍可使用");
            return null;
        } finally {
            emailStates.computeIfPresent(normalizedEmail, (key, state) -> {
                state.sending = false;
                return state;
            });
        }
    }

    @Override
    public void verifyCode(String email, String code, EmailCodePurpose purpose) {
        String normalizedEmail = normalizeAndValidate(email);
        validatePurpose(purpose);
        if (code == null || !code.matches("^[0-9]{6}$")) {
            Asserts.fail("请输入6位邮箱验证码");
        }
        // 校验与消费在同一次原子操作完成，两个并发请求不能重复使用同一验证码。
        AtomicReference<String> failure = new AtomicReference<>("验证码已过期，请重新获取");
        emailStates.computeIfPresent(normalizedEmail, (key, state) -> {
            VerificationCode storedValue = state.codes.get(purpose);
            if (storedValue == null || storedValue.expiresAt <= clock.millis()) {
                state.codes.remove(purpose);
                return state;
            }
            boolean matches = MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),
                    storedValue.value.getBytes(StandardCharsets.UTF_8));
            if (matches) {
                state.codes.remove(purpose);
                failure.set(null);
            } else if (++storedValue.attempts >= maxAttempts) {
                state.codes.remove(purpose);
                failure.set("验证码错误次数过多，请重新获取");
            } else {
                failure.set("邮箱验证码错误，还可尝试" + (maxAttempts - storedValue.attempts) + "次");
            }
            return state;
        });
        if (failure.get() != null) {
            Asserts.fail(failure.get());
        }
    }

    /** 单机开发无需额外服务；定时回收验证码和过期限流状态，避免内存持续增长。 */
    @Scheduled(fixedDelay = 60_000)
    public void removeExpiredStates() {
        long now = clock.millis();
        emailStates.forEach((email, ignored) -> emailStates.computeIfPresent(email, (key, state) -> {
            state.codes.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
            if (!state.sending && state.codes.isEmpty() && state.cooldownUntil <= now && state.hourlyExpiresAt <= now) {
                return null;
            }
            return state;
        }));
    }

    public static String normalizeAndValidate(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!QQ_EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            Asserts.fail("请输入正确的QQ邮箱");
        }
        return normalizedEmail;
    }

    private static void validatePurpose(EmailCodePurpose purpose) {
        if (purpose == null) {
            Asserts.fail("请选择正确的验证码用途");
        }
    }

    private long remainingSeconds(long expiresAt, long now) {
        return Math.max(1, (expiresAt - now + 999) / 1000);
    }

    private String subjectFor(EmailCodePurpose purpose) {
        return purpose == EmailCodePurpose.REGISTER ? "Mall商城注册验证码" : (purpose == EmailCodePurpose.CHANGE_PASSWORD ? "Mall商城修改密码验证码" : "Mall商城密码重置验证码");
    }

    private static class VerificationCode {
        private final String value;
        private final long expiresAt;
        private int attempts;

        private VerificationCode(String value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    private static class EmailState {
        private boolean sending;
        private long cooldownUntil;
        private long hourlyExpiresAt;
        private long sentCount;
        private final EnumMap<EmailCodePurpose, VerificationCode> codes = new EnumMap<>(EmailCodePurpose.class);
    }
}
