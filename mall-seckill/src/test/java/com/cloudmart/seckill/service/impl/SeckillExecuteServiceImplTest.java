package com.cloudmart.seckill.service.impl;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.dto.SeckillExecuteRequest;
import com.cloudmart.seckill.dto.SeckillMessage;
import com.cloudmart.seckill.dto.SeckillResultDTO;
import com.cloudmart.seckill.entity.SeckillActivity;
import com.cloudmart.seckill.entity.SeckillProduct;
import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.mq.SeckillMQProducer;
import com.cloudmart.seckill.repository.SeckillActivityMapper;
import com.cloudmart.seckill.repository.SeckillProductMapper;
import com.cloudmart.seckill.service.SeckillProductService;
import com.cloudmart.seckill.service.SeckillRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T09 秒杀执行编排测试：requestId 贯穿、Redis 只预筛、发送未知不回补、
 * DB 事实权威（售罄/既有资格）、结果查询走请求事实。
 */
class SeckillExecuteServiceImplTest {

    private SeckillActivityMapper activityMapper;
    private SeckillProductMapper productMapper;
    private SeckillProductService seckillProductService;
    private SeckillRequestService requestService;
    private StringRedisTemplate redisTemplate;
    private SeckillMQProducer mqProducer;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;
    private SeckillExecuteServiceImpl seckillService;

    private static final Long USER_ID = 1001L;
    private static final Long ACTIVITY_ID = 2001L;
    private static final Long SECKILL_PRODUCT_ID = 3001L;
    private static final Long SKU_ID = 4001L;
    private static final String REQUEST_ID = "req-abc-000001";

    private SeckillActivity ongoingActivity;
    private SeckillProduct seckillProduct;

    @BeforeEach
    void setUp() {
        activityMapper = mock(SeckillActivityMapper.class);
        productMapper = mock(SeckillProductMapper.class);
        seckillProductService = mock(SeckillProductService.class);
        requestService = mock(SeckillRequestService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        mqProducer = mock(SeckillMQProducer.class);
        valueOperations = mock(ValueOperations.class);
        setOperations = mock(SetOperations.class);

        seckillService = new SeckillExecuteServiceImpl(
                activityMapper, productMapper, seckillProductService, requestService,
                redisTemplate, mqProducer
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

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
        seckillProduct.setStatus("ON_SHELF");
        seckillProduct.setTotalStock(100);
        seckillProduct.setAvailableStock(100);

        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity);
        when(productMapper.selectById(SECKILL_PRODUCT_ID)).thenReturn(seckillProduct);
        when(requestService.findByUser(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID)).thenReturn(null);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(1L);
    }

    private SeckillRequest heldRequest() {
        SeckillRequest request = new SeckillRequest();
        request.setId(1L);
        request.setRequestId(REQUEST_ID);
        request.setActivityId(ACTIVITY_ID);
        request.setProductId(SECKILL_PRODUCT_ID);
        request.setSkuId(SKU_ID);
        request.setUserId(USER_ID);
        request.setQuantity(1);
        request.setSeckillPrice(new BigDecimal("99.00"));
        request.setStatus(SeckillRequest.STATUS_PENDING);
        request.setSendAttempts(0);
        return request;
    }

    @Nested
    @DisplayName("executeSeckill")
    class ExecuteSeckillTests {

        @Test
        @DisplayName("活动不存在 → ACTIVITY_NOT_FOUND")
        void executeSeckill_activityNotFound_throwsException() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

            assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ACTIVITY_NOT_FOUND"));
        }

        @Test
        @DisplayName("活动不在进行中 → FAILED（不建请求）")
        void executeSeckill_activityNotOngoing_returnsFailed() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            ongoingActivity.setStatus("ENDED");

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("未开始或已结束");
            assertThat(result.requestId()).isNull();
            verify(requestService, never()).holdSeat(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("T09：秒杀商品不属于该活动 → PRODUCT_ACTIVITY_MISMATCH")
        void executeSeckill_activityMismatch_throwsException() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            seckillProduct.setActivityId(9999L);

            assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("PRODUCT_ACTIVITY_MISMATCH"));
        }

        @Test
        @DisplayName("商品不存在 → PRODUCT_NOT_FOUND")
        void executeSeckill_productNotFound_throwsException() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(productMapper.selectById(SECKILL_PRODUCT_ID)).thenReturn(null);

            assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
        }

        @Test
        @DisplayName("T09：既有 PENDING 请求幂等返回原 requestId（不重复扣减不发消息）")
        void executeSeckill_existingPending_returnsOriginalRequest() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(requestService.findByUser(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID)).thenReturn(heldRequest());

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.requestId()).isEqualTo(REQUEST_ID);
            assertThat(result.statusUrl()).isEqualTo("/api/seckill/requests/" + REQUEST_ID);
            verify(mqProducer, never()).sendSeckillMessage(any());
        }

        @Test
        @DisplayName("T09：既有 SUCCESS 请求幂等返回 orderId")
        void executeSeckill_existingSuccess_returnsOrderId() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            SeckillRequest existing = heldRequest();
            existing.setStatus(SeckillRequest.STATUS_SUCCESS);
            existing.setOrderId(555L);
            when(requestService.findByUser(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID)).thenReturn(existing);

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("SUCCESS");
            assertThat(result.orderId()).isEqualTo(555L);
        }

        @Test
        @DisplayName("Redis 预筛售罄 → FAILED")
        void executeSeckill_soldOut_returnsFailed() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                    .thenReturn(0L);

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("售罄");
        }

        @Test
        @DisplayName("T09：Redis 报重复但 DB 无事实（陈旧投影）→ 移除集合成员继续，以 DB 唯一键裁决")
        void executeSeckill_staleDuplicateSet_proceedsToDbAuthority() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                    .thenReturn(2L);
            when(requestService.holdSeat(eq(USER_ID), eq(ACTIVITY_ID), eq(SECKILL_PRODUCT_ID),
                    eq(SKU_ID), any(), eq(1))).thenReturn(heldRequest());

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("PENDING");
            verify(setOperations).remove(anyString(), eq(USER_ID.toString()));
        }

        @Test
        @DisplayName("T09：抢购成功 → PENDING + requestId，消息携带 requestId 与价格快照")
        void executeSeckill_success_returnsPendingWithRequestId() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(requestService.holdSeat(eq(USER_ID), eq(ACTIVITY_ID), eq(SECKILL_PRODUCT_ID),
                    eq(SKU_ID), eq(new BigDecimal("99.00")), eq(1))).thenReturn(heldRequest());

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.requestId()).isEqualTo(REQUEST_ID);
            assertThat(result.statusUrl()).isEqualTo("/api/seckill/requests/" + REQUEST_ID);
            ArgumentCaptor<SeckillMessage> captor = ArgumentCaptor.forClass(SeckillMessage.class);
            verify(mqProducer).sendSeckillMessage(captor.capture());
            assertThat(captor.getValue().requestId()).isEqualTo(REQUEST_ID);
            assertThat(captor.getValue().seckillPrice()).isEqualByComparingTo("99.00");
            assertThat(captor.getValue().skuId()).isEqualTo(SKU_ID);
        }

        @Test
        @DisplayName("T09：MQ 发送失败 → 不回补占用，仍返回 PENDING（恢复任务按事实收口）")
        void executeSeckill_mqSendFails_staysPendingNoRollback() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(requestService.holdSeat(eq(USER_ID), eq(ACTIVITY_ID), eq(SECKILL_PRODUCT_ID),
                    eq(SKU_ID), any(), eq(1))).thenReturn(heldRequest());
            doThrow(new BusinessException("MQ_SEND_FAILED", "MQ发送失败"))
                    .when(mqProducer).sendSeckillMessage(any());

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.requestId()).isEqualTo(REQUEST_ID);
            // 发送未知 ≠ 未发送：不得回补 Redis（盲目回补会双卖）
            verify(valueOperations, never()).increment(anyString());
            verify(setOperations, never()).remove(anyString(), anyString());
        }

        @Test
        @DisplayName("T09：DB 口径售罄 → 回退 Redis 预扣并 FAILED")
        void executeSeckill_dbSoldOut_rollsBackRedisProjection() {
            SeckillExecuteRequest request = new SeckillExecuteRequest(ACTIVITY_ID, SECKILL_PRODUCT_ID);
            when(requestService.holdSeat(eq(USER_ID), eq(ACTIVITY_ID), eq(SECKILL_PRODUCT_ID),
                    eq(SKU_ID), any(), eq(1)))
                    .thenThrow(new SeckillRequestService.SeatSoldOutException(SECKILL_PRODUCT_ID));

            SeckillResultDTO result = seckillService.executeSeckill(USER_ID, request);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("售罄");
            verify(valueOperations).increment(anyString());
            verify(setOperations).remove(anyString(), eq(USER_ID.toString()));
        }
    }

    @Nested
    @DisplayName("executeSeckillBlockHandler")
    class BlockHandlerTests {

        @Test
        @DisplayName("Sentinel 限流 → FAILED 限流文案")
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
        @DisplayName("无请求事实 → FAILED 未找到")
        void getSeckillResult_notFound_returnsFailed() {
            when(requestService.findByUser(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID)).thenReturn(null);

            SeckillResultDTO result = seckillService.getSeckillResult(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("未找到");
        }

        @Test
        @DisplayName("PENDING 事实 → PENDING + requestId（DB 事实，可重建）")
        void getSeckillResult_pendingStatus() {
            when(requestService.findByUser(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID)).thenReturn(heldRequest());

            SeckillResultDTO result = seckillService.getSeckillResult(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID);

            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.requestId()).isEqualTo(REQUEST_ID);
        }

        @Test
        @DisplayName("SUCCESS 事实 → SUCCESS + orderId")
        void getSeckillResult_successStatus() {
            SeckillRequest existing = heldRequest();
            existing.setStatus(SeckillRequest.STATUS_SUCCESS);
            existing.setOrderId(12345L);
            when(requestService.findByUser(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID)).thenReturn(existing);

            SeckillResultDTO result = seckillService.getSeckillResult(USER_ID, ACTIVITY_ID, SECKILL_PRODUCT_ID);

            assertThat(result.status()).isEqualTo("SUCCESS");
            assertThat(result.orderId()).isEqualTo(12345L);
        }

        @Test
        @DisplayName("T09：按 requestId 查询 → 本人请求返回状态")
        void getSeckillResultByRequest_ownedRequest_returnsStatus() {
            when(requestService.findByRequestId(REQUEST_ID)).thenReturn(heldRequest());

            SeckillResultDTO result = seckillService.getSeckillResultByRequest(USER_ID, REQUEST_ID);

            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.requestId()).isEqualTo(REQUEST_ID);
        }

        @Test
        @DisplayName("T09：按 requestId 查他人请求 → 拒绝（归属校验）")
        void getSeckillResultByRequest_otherUserRequest_rejected() {
            when(requestService.findByRequestId(REQUEST_ID)).thenReturn(heldRequest());

            SeckillResultDTO result = seckillService.getSeckillResultByRequest(999L, REQUEST_ID);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("未找到");
        }
    }
}
