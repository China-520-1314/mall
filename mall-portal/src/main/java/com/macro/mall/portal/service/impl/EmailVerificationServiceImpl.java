package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.portal.domain.EmailCodePurpose;
import com.macro.mall.portal.service.EmailVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {
    private static final Pattern QQ_EMAIL_PATTERN = Pattern.compile("^[1-9][0-9]{4,11}@qq\\.com$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
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
    public void sendCode(String email, EmailCodePurpose purpose) {
        String normalizedEmail = normalizeAndValidate(email);
        if (sender == null || sender.isBlank()) {
            Asserts.fail("邮件服务未配置，请设置QQ_MAIL_USERNAME和QQ_MAIL_AUTH_CODE");
        }

        String cooldownKey = cooldownKey(normalizedEmail);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, Boolean.TRUE, Duration.ofSeconds(cooldownSeconds));
        if (!Boolean.TRUE.equals(acquired)) {
            Asserts.fail("操作太频繁，请10秒后再试");
        }

        String hourlyKey = hourlyKey(normalizedEmail);
        Long sentCount = redisTemplate.opsForValue().increment(hourlyKey);
        if (sentCount != null && sentCount == 1L) {
            redisTemplate.expire(hourlyKey, Duration.ofHours(1));
        }
        if (sentCount == null || sentCount > hourlyLimit) {
            Asserts.fail("该邮箱一小时内获取验证码次数已达上限");
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String codeKey = codeKey(normalizedEmail, purpose);
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofSeconds(codeExpireSeconds));
        redisTemplate.delete(attemptKey(normalizedEmail, purpose));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(normalizedEmail);
        message.setSubject(subjectFor(purpose));
        message.setText("您的验证码是：" + code + "\n\n验证码5分钟内有效，请勿转发给他人。若非本人操作，请忽略此邮件。");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            redisTemplate.delete(List.of(codeKey, cooldownKey));
            Asserts.fail("验证码邮件发送失败，请检查QQ邮箱SMTP配置");
        }
    }

    @Override
    public void verifyCode(String email, String code, EmailCodePurpose purpose) {
        String normalizedEmail = normalizeAndValidate(email);
        if (code == null || !code.matches("^[0-9]{6}$")) {
            Asserts.fail("请输入6位邮箱验证码");
        }

        String codeKey = codeKey(normalizedEmail, purpose);
        String attemptKey = attemptKey(normalizedEmail, purpose);
        Object storedValue = redisTemplate.opsForValue().get(codeKey);
        if (storedValue == null) {
            Asserts.fail("验证码已过期，请重新获取");
        }

        boolean matches = MessageDigest.isEqual(
                code.getBytes(StandardCharsets.UTF_8),
                String.valueOf(storedValue).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(attemptKey, Duration.ofSeconds(codeExpireSeconds));
            }
            if (attempts != null && attempts >= maxAttempts) {
                redisTemplate.delete(List.of(codeKey, attemptKey));
                Asserts.fail("验证码错误次数过多，请重新获取");
            }
            Asserts.fail("邮箱验证码错误");
        }

        redisTemplate.delete(List.of(codeKey, attemptKey));
    }

    public static String normalizeAndValidate(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!QQ_EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            Asserts.fail("请输入正确的QQ邮箱");
        }
        return normalizedEmail;
    }

    private String codeKey(String email, EmailCodePurpose purpose) {
        return "mall:ums:email-code:" + purpose.name() + ":" + email;
    }

    private String attemptKey(String email, EmailCodePurpose purpose) {
        return "mall:ums:email-attempt:" + purpose.name() + ":" + email;
    }

    private String cooldownKey(String email) {
        return "mall:ums:email-cooldown:" + email;
    }

    private String hourlyKey(String email) {
        return "mall:ums:email-hourly:" + email;
    }

    private String subjectFor(EmailCodePurpose purpose) {
        return purpose == EmailCodePurpose.REGISTER ? "Mall商城注册验证码" : "Mall商城密码重置验证码";
    }
}
