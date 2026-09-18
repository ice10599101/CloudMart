package com.cloudmart.file.service.impl;

import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UploadQuotaServiceImpl 每日上传配额单元测试")
@ExtendWith(MockitoExtension.class)
class UploadQuotaServiceImplTest {

    private static final String USER_ID = "10001";
    private static final String TODAY = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private UploadQuotaServiceImpl quotaService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        quotaService = new UploadQuotaServiceImpl(redisTemplate, 30, 15, true, "jpg,jpeg,png,gif,bmp,webp,svg");
    }

    private void stubIncrement(long count) {
        when(valueOperations.increment(anyString())).thenReturn(count);
    }

    private String keyOf(String type) {
        return "file:quota:" + type + ":" + USER_ID + ":" + TODAY;
    }

    @Nested
    @DisplayName("reserve 预占额度")
    class ReserveTests {

        @Test
        @DisplayName("图片文件 - 计入 image 桶且未超限时预占成功")
        void shouldReserveImageQuota() {
            stubIncrement(1L);

            boolean reserved = quotaService.reserve(USER_ID, "photo.JPG");

            assertThat(reserved).isTrue();
            verify(valueOperations).increment(keyOf("image"));
            verify(redisTemplate).expireAt(eq(keyOf("image")), any(java.util.Date.class));
        }

        @Test
        @DisplayName("非图片文件（视频/音乐/文档）- 全部计入 other 桶")
        void shouldReserveOtherQuotaForNonImageFiles() {
            stubIncrement(1L);

            assertThat(quotaService.reserve(USER_ID, "clip.mp4")).isTrue();
            assertThat(quotaService.reserve(USER_ID, "song.mp3")).isTrue();
            assertThat(quotaService.reserve(USER_ID, "doc.pdf")).isTrue();
            assertThat(quotaService.reserve(USER_ID, "voice.webm")).isTrue();

            verify(valueOperations, org.mockito.Mockito.times(4)).increment(keyOf("other"));
        }

        @Test
        @DisplayName("图片达到 30 张上限 - 第 31 次抛 UPLOAD_DAILY_LIMIT_EXCEEDED")
        void shouldRejectImageWhenLimitReached() {
            stubIncrement(31L);

            assertThatThrownBy(() -> quotaService.reserve(USER_ID, "photo.jpg"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("UPLOAD_DAILY_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("非图片达到 15 次上限 - 第 16 次抛 UPLOAD_DAILY_LIMIT_EXCEEDED")
        void shouldRejectOtherWhenLimitReached() {
            stubIncrement(16L);

            assertThatThrownBy(() -> quotaService.reserve(USER_ID, "clip.mp4"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("UPLOAD_DAILY_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("Redis 异常 - Fail-Open 放行且不预占额度")
        void shouldFailOpenWhenRedisUnavailable() {
            when(valueOperations.increment(anyString())).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));

            boolean reserved = quotaService.reserve(USER_ID, "photo.jpg");

            assertThat(reserved).isFalse();
        }

        @Test
        @DisplayName("配额开关关闭 - 直接放行且不访问 Redis")
        void shouldSkipWhenDisabled() {
            UploadQuotaServiceImpl disabled = new UploadQuotaServiceImpl(redisTemplate, 30, 15, false, "jpg");

            boolean reserved = disabled.reserve(USER_ID, "photo.jpg");

            assertThat(reserved).isFalse();
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("文件名为 null - 归入 other 桶，不抛异常")
        void shouldTreatNullFilenameAsOther() {
            stubIncrement(1L);

            assertThat(quotaService.reserve(USER_ID, null)).isTrue();
            verify(valueOperations).increment(keyOf("other"));
        }
    }

    @Nested
    @DisplayName("refund 退还额度")
    class RefundTests {

        @Test
        @DisplayName("上传失败 - 按文件类型退还对应桶额度")
        void shouldRefundQuotaOnUploadFailure() {
            stubIncrement(1L);
            quotaService.reserve(USER_ID, "photo.jpg");

            quotaService.refund(USER_ID, "photo.jpg");

            verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(keyOf("image"))));
        }

        @Test
        @DisplayName("Redis 异常 - 退还失败不抛异常")
        void shouldNotThrowWhenRefundFails() {
            when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
                    .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));

            assertThatCode(() -> quotaService.refund(USER_ID, "photo.jpg"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("先预占后超限 - 超限请求不触发退还（由调用方按预占结果决定）")
    void shouldExposeReservationResultForCaller() {
        stubIncrement(31L);

        assertThatThrownBy(() -> quotaService.reserve(USER_ID, "photo.jpg"))
                .isInstanceOf(BusinessException.class);
        verify(redisTemplate, never()).execute(any(RedisScript.class), any(List.class));
    }
}
