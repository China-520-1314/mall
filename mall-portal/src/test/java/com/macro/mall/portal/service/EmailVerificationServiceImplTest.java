package com.macro.mall.portal.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.portal.domain.EmailCodePurpose;
import com.macro.mall.portal.domain.EmailCodeRateLimitException;
import com.macro.mall.portal.domain.EmailCodeSendResult;
import com.macro.mall.portal.service.impl.EmailVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailVerificationServiceImplTest {
    private static final String EMAIL = "12345678@qq.com";
    private static final EmailCodePurpose REGISTER = EmailCodePurpose.REGISTER;
    private static final EmailCodePurpose RESET = EmailCodePurpose.RESET_PASSWORD;
    private EmailVerificationServiceImpl service;
    private JavaMailSender mailSender;
    private AtomicLong now;
    private AtomicReference<SimpleMailMessage> lastMessage;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationServiceImpl();
        mailSender = mock(JavaMailSender.class);
        Clock clock = mock(Clock.class);
        now = new AtomicLong(1_800_000_000_000L);
        lastMessage = new AtomicReference<>();
        when(clock.millis()).thenAnswer(invocation -> now.get());
        doAnswer(invocation -> {
            lastMessage.set(invocation.getArgument(0));
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "sender", "87654321@qq.com");
        ReflectionTestUtils.setField(service, "clock", clock);
        ReflectionTestUtils.setField(service, "codeExpireSeconds", 300L);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 10L);
        ReflectionTestUtils.setField(service, "hourlyLimit", 5L);
        ReflectionTestUtils.setField(service, "maxAttempts", 5L);
    }

    @Test
    void successReturnsNormalizedEmailAndActualDurationsWithoutCode() {
        EmailCodeSendResult result = service.sendCode(" 12345678@QQ.COM ", REGISTER);
        assertEquals(EMAIL, result.email());
        assertEquals(10, result.cooldownSeconds());
        assertEquals(300, result.expiresIn());
        assertArrayEquals(new String[]{EMAIL}, lastMessage.get().getTo());
        assertTrue(lastMessage.get().getSubject().contains("注册"));
        assertEquals(6, codeFromLastMessage().length());
    }

    @Test
    void cooldownIsSharedAcrossPurposesAndAllowsRetryAtTenSeconds() {
        service.sendCode(EMAIL, REGISTER);
        now.addAndGet(9_001);
        EmailCodeRateLimitException failure = assertThrows(EmailCodeRateLimitException.class,
                () -> service.sendCode("12345678@QQ.COM", RESET));
        assertEquals(1, failure.getRetryAfterSeconds());
        now.addAndGet(999);
        assertDoesNotThrow(() -> service.sendCode(EMAIL, RESET));
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void slowSendDoesNotAllowParallelEmailEvenAfterCooldownAndCleanup() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            assertTrue(finish.await(5, TimeUnit.SECONDS));
            lastMessage.set(invocation.getArgument(0));
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<EmailCodeSendResult> pending = pool.submit(() -> service.sendCode(EMAIL, REGISTER));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            now.addAndGet(60_000);
            service.removeExpiredStates();
            assertThrows(EmailCodeRateLimitException.class, () -> service.sendCode(EMAIL, REGISTER));
            verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
            finish.countDown();
            assertNotNull(pending.get(5, TimeUnit.SECONDS));
            now.addAndGet(299_999);
            assertDoesNotThrow(() -> service.verifyCode(EMAIL, codeFromLastMessage(), REGISTER));
        } finally {
            finish.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void failedResendKeepsPreviousCodeAndDoesNotConsumeHourlyQuota() {
        ReflectionTestUtils.setField(service, "hourlyLimit", 2L);
        service.sendCode(EMAIL, REGISTER);
        String original = codeFromLastMessage();
        now.addAndGet(10_000);
        doThrow(new MailSendException("test mail failure")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThrows(ApiException.class, () -> service.sendCode(EMAIL, REGISTER));
        assertDoesNotThrow(() -> service.verifyCode(EMAIL, original, REGISTER));
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(() -> service.sendCode(EMAIL, REGISTER));
        now.addAndGet(10_000);
        assertThrows(EmailCodeRateLimitException.class, () -> service.sendCode(EMAIL, REGISTER));
        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    void codeCannotBeUsedForAnotherPurposeOrAfterFiveWrongAttempts() {
        service.sendCode(EMAIL, REGISTER);
        String code = codeFromLastMessage();
        assertThrows(ApiException.class, () -> service.verifyCode(EMAIL, code, RESET));
        String wrongCode = code.equals("000000") ? "000001" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            ApiException failure = assertThrows(ApiException.class,
                    () -> service.verifyCode(EMAIL, wrongCode, REGISTER));
            if (attempt == 5) assertTrue(failure.getMessage().contains("错误次数过多"));
        }
        assertThrows(ApiException.class, () -> service.verifyCode(EMAIL, code, REGISTER));
    }

    @Test
    void concurrentVerificationConsumesCodeExactlyOnce() throws Exception {
        service.sendCode(EMAIL, REGISTER);
        String code = codeFromLastMessage();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> verify = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    service.verifyCode(EMAIL, code, REGISTER);
                    return true;
                } catch (ApiException exception) {
                    return false;
                }
            };
            Future<Boolean> first = pool.submit(verify);
            Future<Boolean> second = pool.submit(verify);
            start.countDown();
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void expiredCodeFailsAndHourlyLimitSurvivesCodeCleanup() {
        ReflectionTestUtils.setField(service, "hourlyLimit", 1L);
        service.sendCode(EMAIL, REGISTER);
        String code = codeFromLastMessage();
        now.addAndGet(300_000);
        service.removeExpiredStates();
        assertThrows(ApiException.class, () -> service.verifyCode(EMAIL, code, REGISTER));
        EmailCodeRateLimitException failure = assertThrows(EmailCodeRateLimitException.class,
                () -> service.sendCode(EMAIL, REGISTER));
        assertEquals(3300, failure.getRetryAfterSeconds());
        now.addAndGet(3_300_000);
        service.removeExpiredStates();
        Map<?, ?> states = (Map<?, ?>) ReflectionTestUtils.getField(service, "emailStates");
        assertNotNull(states);
        assertTrue(states.isEmpty());
        assertDoesNotThrow(() -> service.sendCode(EMAIL, REGISTER));
    }

    @Test
    void oneEmailCooldownDoesNotBlockAnotherEmailAndInvalidInputNeverSends() {
        service.sendCode(EMAIL, REGISTER);
        assertDoesNotThrow(() -> service.sendCode("22345678@qq.com", REGISTER));
        assertThrows(ApiException.class, () -> service.sendCode("12345678@example.com", REGISTER));
        assertThrows(ApiException.class, () -> service.sendCode(EMAIL, null));
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    private String codeFromLastMessage() {
        Matcher matcher = Pattern.compile("验证码是：([0-9]{6})").matcher(lastMessage.get().getText());
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}
