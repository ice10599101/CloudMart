package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WishAiConversation;
import com.cloudmart.wish.enums.AiPromptScene;
import com.cloudmart.wish.enums.AiScene;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.service.AiConfigService;
import com.cloudmart.wish.service.AiPromptService;
import com.cloudmart.wish.service.AssistantAiClient;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.vo.AnnualReportVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import com.cloudmart.wish.config.WishCryptoProperties;
import com.cloudmart.wish.util.ContentCipher;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnnualReportGenerator 单元测试")
class AnnualReportGeneratorTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private AssistantAiClient assistantAiClient;
    @Mock
    private AiPromptService aiPromptService;
    @Mock
    private ConsentService consentService;
    @Mock
    private AiConfigService aiConfigService;
    @Mock
    private WishAiConversationMapper conversationMapper;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final AiPrivacySanitizer privacySanitizer = new AiPrivacySanitizer();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private AnnualReportGenerator generator;

    private static final Long USER_ID = 1001L;
    private static final int YEAR = 2026;

    @BeforeEach
    void setUp() {
        generator = new AnnualReportGenerator(redisTemplate, new ContentCipher(new WishCryptoProperties()),
                objectMapper, assistantAiClient,
                aiPromptService, consentService, aiConfigService, privacySanitizer,
                conversationMapper, transactionTemplate);
    }

    private AnnualReportVO templateReport() {
        return new AnnualReportVO(YEAR, 8, 120, "模板降级文案",
                List.of(new AnnualReportVO.Milestone(LocalDate.of(YEAR, 6, 1), "记下了一段成长", "内容")),
                List.of(new AnnualReportVO.TopCategory("健康", 5)));
    }

    @Nested
    @DisplayName("任务锁（幂等）")
    class LockTest {

        @Test
        @DisplayName("SETNX 成功 → 获锁执行")
        void acquireLockWhenAbsent() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);

            assertThat(generator.tryLock(USER_ID, YEAR)).isTrue();
        }

        @Test
        @DisplayName("SETNX 失败 → 已有任务进行中")
        void refuseLockWhenPresent() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(false);

            assertThat(generator.tryLock(USER_ID, YEAR)).isFalse();
        }

        @Test
        @DisplayName("Redis 异常 → Fail-Open 放行（重复生成结果幂等覆盖）")
        void failOpenOnRedisError() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenThrow(new DataAccessException("redis down") {
                    });

            assertThat(generator.tryLock(USER_ID, YEAR)).isTrue();
        }
    }

    @Nested
    @DisplayName("异步生成")
    class GenerateTest {

        @Test
        @DisplayName("已同意 + AI 成功 → 缓存 AI 版报告并写对话（USER+ASSISTANT）")
        void generateAiSummaryAndCache() {
            when(consentService.hasGrantedAiDataProcessing(USER_ID)).thenReturn(true);
            when(aiPromptService.getActivePrompt(AiPromptScene.ANNUAL_REPORT, USER_ID))
                    .thenReturn("系统提示词");
            when(assistantAiClient.generateText(anyString(), anyString()))
                    .thenReturn("AI 生成的成长总结");
            when(aiConfigService.getIntConfig(AiConfigService.KEY_ANNUAL_REPORT_TTL_HOURS, 168))
                    .thenReturn(168);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            doAnswer(invocation -> {
                invocation.getArgument(0, java.util.function.Consumer.class).accept(null);
                return null;
            }).when(transactionTemplate).executeWithoutResult(any());

            generator.generateAsync(USER_ID, YEAR, templateReport());

            // 缓存 AI 完整版
            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), jsonCaptor.capture(), eq(168L), eq(TimeUnit.HOURS));
            assertThat(jsonCaptor.getValue()).contains("AI 生成的成长总结");
            // 对话两条：USER=数据摘要，ASSISTANT=growthSummary（scene=ANNUAL_REPORT）
            ArgumentCaptor<WishAiConversation> conversationCaptor =
                    ArgumentCaptor.forClass(WishAiConversation.class);
            verify(conversationMapper, org.mockito.Mockito.times(2)).insert(conversationCaptor.capture());
            List<WishAiConversation> records = conversationCaptor.getAllValues();
            assertThat(records).allSatisfy(record -> {
                assertThat(record.getScene()).isEqualTo(AiScene.ANNUAL_REPORT);
                assertThat(record.getSessionId()).isEqualTo("report-" + USER_ID + "-" + YEAR);
            });
            assertThat(records.get(0).getContent()).contains("实现心愿 8 个");
            assertThat(records.get(1).getContent()).isEqualTo("AI 生成的成长总结");
        }

        @Test
        @DisplayName("未同意 AI 协议 → 不调大模型，直接缓存模板版")
        void fallbackToTemplateWhenNoConsent() {
            when(consentService.hasGrantedAiDataProcessing(USER_ID)).thenReturn(false);
            when(aiConfigService.getIntConfig(AiConfigService.KEY_ANNUAL_REPORT_TTL_HOURS, 168))
                    .thenReturn(168);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            generator.generateAsync(USER_ID, YEAR, templateReport());

            verify(assistantAiClient, never()).generateText(anyString(), anyString());
            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), jsonCaptor.capture(), eq(168L), eq(TimeUnit.HOURS));
            assertThat(jsonCaptor.getValue()).contains("模板降级文案");
        }

        @Test
        @DisplayName("AI 生成失败 → 清除任务锁等待重试")
        void releaseLockOnAiFailure() {
            when(consentService.hasGrantedAiDataProcessing(USER_ID)).thenReturn(true);
            when(aiPromptService.getActivePrompt(AiPromptScene.ANNUAL_REPORT, USER_ID))
                    .thenReturn("系统提示词");
            when(assistantAiClient.generateText(anyString(), anyString()))
                    .thenThrow(new com.cloudmart.common.exception.BusinessException(
                            WishErrorCodes.WISH_AI_UNAVAILABLE, "AI 暂不可用"));

            generator.generateAsync(USER_ID, YEAR, templateReport());

            verify(redisTemplate).delete("wish:annual_report:lock:user:" + USER_ID + ":" + YEAR);
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    @DisplayName("缓存读取")
    class ReadCacheTest {

        @Test
        @DisplayName("命中 → 反序列化返回报告")
        void readCachedReport() throws com.fasterxml.jackson.core.JsonProcessingException {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            String json = objectMapper.writeValueAsString(templateReport());
            when(valueOperations.get("wish:annual_report:user:" + USER_ID + ":" + YEAR))
                    .thenReturn(json);

            AnnualReportVO result = generator.readCache(USER_ID, YEAR);

            assertThat(result).isNotNull();
            assertThat(result.growthSummary()).isEqualTo("模板降级文案");
            assertThat(result.fulfilledCount()).isEqualTo(8);
        }

        @Test
        @DisplayName("未命中 → 返回 null")
        void returnNullWhenMiss() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);

            assertThat(generator.readCache(USER_ID, YEAR)).isNull();
        }

        @Test
        @DisplayName("缓存 JSON 损坏 → 返回 null（回退同步聚合）")
        void returnNullOnCorruptedCache() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn("not-a-json");

            assertThat(generator.readCache(USER_ID, YEAR)).isNull();
        }
    }
}
