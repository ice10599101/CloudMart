package com.cloudmart.seckill.service.impl;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.dto.SeckillExecuteRequest;
import com.cloudmart.seckill.dto.SeckillResultDTO;
import com.cloudmart.seckill.entity.SeckillActivity;
import com.cloudmart.seckill.entity.SeckillProduct;
import com.cloudmart.seckill.mq.SeckillMQProducer;
import com.cloudmart.seckill.repository.SeckillActivityMapper;
import com.cloudmart.seckill.repository.SeckillProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillExecuteServiceImplTest {

    private SeckillActivityMapper activityMapper;
    private SeckillProductMapper productMapper;
    private StringRedisTemplate redisTemplate;
    private SeckillMQProducer mqProducer;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;
    private SeckillExecuteServiceImpl seckillService;

    private static final Long USER_ID = 1001L;
    private static final Long ACTIVITY_ID = 2001L;
    private static final Long SECKILL_PRODUCT_ID = 3001L;
    private static final Long SKU_ID = 4001L;

    private SeckillActivity ongoingActivity;
    private SeckillProduct seckillProduct;

    @BeforeEach
    void setUp() {
        activityMapper = mock(SeckillActivityMapper.class);
        productMapper = mock(SeckillProductMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        mqProducer = mock(SeckillMQProducer.class);
        valueOperations = mock(ValueOperations.class);
        setOperations = mock(SetOperations.class);

        seckillService = new SeckillExecuteServiceImpl(
                activityMapper, productMapper, redisTemplate, mqProducer
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        ongoingActivity = new SeckillActivity();
        ongoingActivity.setId(ACTIVITY_ID);
        ongoingActivity.setName("Test Seckill");
        ongoingActivity.setStatus("ONGOING");
        ongoingActivity.setStartTime(LocalDateTime.now().minusHours(1));
        ongoingActivity.setEndTime(LocalDateTime.now().plusHours(1));

        seckillProduct = new SeckillProduct();
        seckillProduct.setId(SECKILL_PRODUCT_ID);
        seckillProduct.setActivityId(ACTIVITY_ID);
        seckillProduct.setSkuId(SKU_ID);
        seckillProduct.setSeckillPrice(new BigDecimal("99.00"));
        seckillProduct.setOriginalPrice(new BigDecimal("199.00"));
    }

    @Nested
    @DisplayName("executeSeckill")
    class ExecuteSeckillTests {

        @Test
        @DisplayName("should throw when activity not found")
        void executeSeckill_activityNotFound_throwsException() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

            assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ACTIVITY_NOT_FOUND"));
        }

        @Test
        @DisplayName("should return FAILED when activity is not ongoing")
        void executeSeckill_activityNotOngoing_returnsFailed() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            ongoingActivity.setStatus("ENDED");
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity);

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("未开始或已结束");
        }

        @Test
        @DisplayName("should throw when product not found")
        void executeSeckill_productNotFound_throwsException() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity);
            when(productMapper.selectById(SECKILL_PRODUCT_ID)).thenReturn(null);

            assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
        }

        @Test
        @DisplayName("should return FAILED when sold out (Lua returns 0)")
        void executeSeckill_soldOut_returnsFailed() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity);
            when(productMapper.selectById(SECKILL_PRODUCT_ID)).thenReturn(seckillProduct);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                    .thenReturn(0L);

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("售罄");
        }

        @Test
        @DisplayName("should return FAILED when duplicate purchase (Lua returns 2)")
        void executeSeckill_duplicatePurchase_returnsFailed() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity);
            when(productMapper.selectById(SECKILL_PRODUCT_ID)).thenReturn(seckillProduct);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                    .thenReturn(2L);

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("重复");
        }

        @Test
        @DisplayName("should return PENDING when seckill succeeds (Lua returns 1)")
        void executeSeckill_success_returnsPending() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity);
            when(productMapper.selectById(SECKILL_PRODUCT_ID)).thenReturn(seckillProduct);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                    .thenReturn(1L);

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("PENDING");
            verify(mqProducer).sendSeckillMessage(any());
            verify(valueOperations).set(anyString(), eq("PENDING"), any());
        }

        @Test
        @DisplayName("should return FAILED and rollback Redis when MQ send fails")
        void executeSeckill_mqSendFails_rollbacksRedis() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity);
            when(productMapper.selectById(SECKILL_PRODUCT_ID)).thenReturn(seckillProduct);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                    .thenReturn(1L);
            doThrow(new BusinessException("MQ_SEND_FAILED", "MQ发送失败"))
                    .when(mqProducer).sendSeckillMessage(any());

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("FAILED");
            verify(valueOperations).increment(anyString());
            verify(setOperations).remove(anyString(), eq(USER_ID.toString()));
            verify(redisTemplate).delete(anyString());
        }

        @Test
        @DisplayName("should return FAILED when sold out marker is present")
        void executeSeckill_soldOutMarker_returnsFailed() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity);
            when(productMapper.selectById(SECKILL_PRODUCT_ID)).thenReturn(seckillProduct);

            seckillService.clearSoldOutMarker(ACTIVITY_ID.toString(), SECKILL_PRODUCT_ID.toString());

            String stockKey = "seckill:stock:" + ACTIVITY_ID + ":" + SECKILL_PRODUCT_ID;
            try {
                var field = SeckillExecuteServiceImpl.class.getDeclaredField("soldOutMarkers");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                var markers = (java.util.concurrent.ConcurrentHashMap<String, Boolean>) field.get(seckillService);
                markers.put(stockKey, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("售罄");
        }
    }

    @Nested
    @DisplayName("executeSeckillBlockHandler")
    class BlockHandlerTests {

        @Test
        @DisplayName("should return FAILED with rate limit message")
        void executeSeckillBlockHandler_returnsRateLimitedMessage() {
            BlockException blockException = mock(BlockException.class);
            FlowRule rule = new FlowRule();
            when(blockException.getRule()).thenReturn(rule);

            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);

            SeckillResultDTO result = seckillService.executeSeckillBlockHandler(USER_ID, request, blockException);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("频繁");
        }
    }

    @Nested
    @DisplayName("getSeckillResult")
    class GetSeckillResultTests {

        @Test
        @DisplayName("should return FAILED when result not found")
        void getSeckillResult_notFound_returnsFailed() {
            when(valueOperations.get(anyString())).thenReturn(null);

            SeckillResultDTO result = seckillService.getSeckillResult(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("未找到");
        }

        @Test
        @DisplayName("should return PENDING status")
        void getSeckillResult_pendingStatus() {
            when(valueOperations.get(anyString())).thenReturn("PENDING");

            SeckillResultDTO result = seckillService.getSeckillResult(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID);

            assertThat(result.status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("should return SUCCESS status with orderId")
        void getSeckillResult_successStatus() {
            when(valueOperations.get(contains("seckill:result:"))).thenReturn("SUCCESS");
            when(valueOperations.get(contains(":orderId"))).thenReturn("12345");

            SeckillResultDTO result = seckillService.getSeckillResult(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID);

            assertThat(result.status()).isEqualTo("SUCCESS");
            assertThat(result.orderId()).isEqualTo(12345L);
        }
    }
}
