package com.cloudmart.user.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.mail.MailVerificationSender;
import com.cloudmart.user.entity.User;
import com.cloudmart.user.repository.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterCodeServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private MailVerificationSender mailSender;

    private RegisterCodeServiceImpl registerCodeService;

    private static final String CODE_KEY = "user:register:code:";
    private static final String COOLDOWN_KEY = "user:register:code:cooldown:";
    private static final String HOURLY_KEY = "user:register:code:hourly:";

    @BeforeEach
    void setUp() {
        registerCodeService = new RegisterCodeServiceImpl(userMapper, redisTemplate, mailSender);
        ReflectionTestUtils.setField(registerCodeService, "echoCode", false);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Nested
    @DisplayName("sendCode")
    class SendCodeTests {

        @Test
        @DisplayName("email already registered -> throws EMAIL_DUPLICATE and no redis/mail access")
        void sendCode_WhenEmailRegistered_ShouldThrowEmailDuplicate() {
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> registerCodeService.sendCode("dup@example.com"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("EMAIL_DUPLICATE"));
            verify(redisTemplate, never()).hasKey(anyString());
            verify(mailSender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("cooldown key present -> throws USER_REGISTER_CODE_FREQUENT and no mail sent")
        void sendCode_WhenCooldownActive_ShouldThrowFrequent() {
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(redisTemplate.hasKey(COOLDOWN_KEY + "a@example.com")).thenReturn(true);

            assertThatThrownBy(() -> registerCodeService.sendCode("a@example.com"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("USER_REGISTER_CODE_FREQUENT"));
            verify(mailSender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("hourly limit exceeded -> throws USER_REGISTER_CODE_FREQUENT")
        void sendCode_WhenHourlyLimitExceeded_ShouldThrowFrequent() {
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(redisTemplate.hasKey(anyString())).thenReturn(false);
            when(valueOperations.increment(HOURLY_KEY + "a@example.com")).thenReturn(6L);

            assertThatThrownBy(() -> registerCodeService.sendCode("a@example.com"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("USER_REGISTER_CODE_FREQUENT"));
        }

        @Test
        @DisplayName("first send in hour -> hourly counter expires after 1h")
        void sendCode_WhenFirstSendInHour_ShouldExpireHourlyCounter() {
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(redisTemplate.hasKey(anyString())).thenReturn(false);
            when(valueOperations.increment(HOURLY_KEY + "a@example.com")).thenReturn(1L);
            when(mailSender.isConfigured()).thenReturn(false);

            registerCodeService.sendCode("a@example.com");

            verify(redisTemplate).expire(eq(HOURLY_KEY + "a@example.com"), eq(Duration.ofHours(1)));
        }

        @Test
        @DisplayName("mail channel unconfigured -> sent=false with message, no code stored")
        void sendCode_WhenMailUnconfigured_ShouldReturnSentFalse() {
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(redisTemplate.hasKey(anyString())).thenReturn(false);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            when(mailSender.isConfigured()).thenReturn(false);

            var result = registerCodeService.sendCode("a@example.com");

            assertThat(result.sent()).isFalse();
            assertThat(result.message()).isNotBlank();
            verify(valueOperations, never()).set(contains(CODE_KEY), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("mail send success -> stores sha256 hash with 5min TTL, sets 60s cooldown, sent=true")
        void sendCode_WhenMailSent_ShouldStoreHashAndCooldown() {
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(redisTemplate.hasKey(anyString())).thenReturn(false);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            when(mailSender.isConfigured()).thenReturn(true);
            when(mailSender.send(anyString(), anyString(), anyString())).thenReturn(true);

            var result = registerCodeService.sendCode("A@Example.com");

            assertThat(result.sent()).isTrue();
            assertThat(result.echoCode()).isNull();

            // 邮箱统一小写后作为 Redis Key
            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(eq(CODE_KEY + "a@example.com"), hashCaptor.capture(), eq(Duration.ofMinutes(5)));
            assertThat(hashCaptor.getValue()).hasSize(64).isNotEqualTo(hashCaptor.getValue().toUpperCase());
            verify(valueOperations).set(eq(COOLDOWN_KEY + "a@example.com"), eq("1"), eq(Duration.ofSeconds(60)));

            // 邮件正文发给归一化后的邮箱，且包含 6 位验证码
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(mailSender).send(eq("a@example.com"), contains("验证码"), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).matches("(?s).*\\d{6}.*");
        }

        @Test
        @DisplayName("mail send failed -> sent=false and stored code removed (no half success)")
        void sendCode_WhenMailSendFailed_ShouldRemoveCodeAndReturnSentFalse() {
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(redisTemplate.hasKey(anyString())).thenReturn(false);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            when(mailSender.isConfigured()).thenReturn(true);
            when(mailSender.send(anyString(), anyString(), anyString())).thenReturn(false);

            var result = registerCodeService.sendCode("a@example.com");

            assertThat(result.sent()).isFalse();
            assertThat(result.message()).isNotBlank();
            verify(redisTemplate).delete(CODE_KEY + "a@example.com");
        }

        @Test
        @DisplayName("echo-code mode -> code stored and echoed, no mail attempted")
        void sendCode_WhenEchoCodeEnabled_ShouldEchoWithoutMail() {
            ReflectionTestUtils.setField(registerCodeService, "echoCode", true);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            var result = registerCodeService.sendCode("a@example.com");

            assertThat(result.sent()).isTrue();
            assertThat(result.echoCode()).matches("\\d{6}");
            verify(valueOperations).set(eq(CODE_KEY + "a@example.com"), eq(sha256(result.echoCode())),
                    eq(Duration.ofMinutes(5)));
            verify(mailSender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("redis failure on send -> propagates (fail-closed), no mail sent")
        void sendCode_WhenRedisDown_ShouldPropagateAndNotSendMail() {
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(redisTemplate.hasKey(anyString())).thenThrow(new IllegalStateException("redis down"));

            assertThatThrownBy(() -> registerCodeService.sendCode("a@example.com"))
                    .isInstanceOf(IllegalStateException.class);
            verify(mailSender, never()).send(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("verifyAndConsume")
    class VerifyTests {

        @Test
        @DisplayName("malformed code (non-6-digit) -> USER_REGISTER_CODE_INVALID without redis read")
        void verify_WhenMalformed_ShouldThrowInvalid() {
            assertThatThrownBy(() -> registerCodeService.verifyAndConsume("a@example.com", "12345"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("USER_REGISTER_CODE_INVALID"));
            assertThatThrownBy(() -> registerCodeService.verifyAndConsume("a@example.com", "12a456"))
                    .isInstanceOf(BusinessException.class);
            verify(valueOperations, never()).get(anyString());
        }

        @Test
        @DisplayName("code expired (absent in redis) -> USER_REGISTER_CODE_INVALID")
        void verify_WhenExpired_ShouldThrowInvalid() {
            when(valueOperations.get(CODE_KEY + "a@example.com")).thenReturn(null);

            assertThatThrownBy(() -> registerCodeService.verifyAndConsume("a@example.com", "123456"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("USER_REGISTER_CODE_INVALID"));
        }

        @Test
        @DisplayName("wrong code -> USER_REGISTER_CODE_INVALID and not consumed")
        void verify_WhenWrongCode_ShouldThrowInvalidAndKeepCode() {
            when(valueOperations.get(CODE_KEY + "a@example.com")).thenReturn(sha256("123456"));

            assertThatThrownBy(() -> registerCodeService.verifyAndConsume("a@example.com", "654321"))
                    .isInstanceOf(BusinessException.class);
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("correct code (email case-insensitive) -> passes and consumed once")
        void verify_WhenCorrect_ShouldConsume() {
            when(valueOperations.get(CODE_KEY + "a@example.com")).thenReturn(sha256("123456"));

            assertThatCode(() -> registerCodeService.verifyAndConsume("A@Example.com ", "123456"))
                    .doesNotThrowAnyException();
            verify(redisTemplate).delete(CODE_KEY + "a@example.com");
        }
    }

}
