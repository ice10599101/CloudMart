package com.cloudmart.seckill.mq;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.dto.SeckillMessage;
import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.feign.OrderQueryFeignClient;
import com.cloudmart.seckill.service.SeckillRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T09 恢复任务测试：发送未知不直接回补——先按请求事实重发（消费侧幂等），
 * 重试耗尽后对账：订单已建补 SUCCESS，确认未建才终态失败并释放占用。
 */
class SeckillRequestRecoveryTest {

    private SeckillRequestService requestService;
    private SeckillMQProducer mqProducer;
    private OrderQueryFeignClient orderQueryFeignClient;
    private StringRedisTemplate redisTemplate;
    private SeckillRequestRecovery recovery;

    @BeforeEach
    void setUp() {
        requestService = mock(SeckillRequestService.class);
        mqProducer = mock(SeckillMQProducer.class);
        orderQueryFeignClient = mock(OrderQueryFeignClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(true);
        recovery = new SeckillRequestRecovery(requestService, mqProducer, orderQueryFeignClient, redisTemplate);
    }

    private SeckillRequest pending(int attempts) {
        SeckillRequest request = new SeckillRequest();
        request.setRequestId("req-rec-1");
        request.setActivityId(1L);
        request.setProductId(2L);
        request.setSkuId(3L);
        request.setUserId(4L);
        request.setStatus("PENDING");
        request.setSendAttempts(attempts);
        return request;
    }

    @Test
    @DisplayName("重发窗口（attempts<3）：按快照重发消息并登记退避")
    void recover_resendWindow_resendsWithBackoff() {
        when(requestService.findPendingDue(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(pending(0)));
        when(requestService.scheduleRetry(eq("req-rec-1"), eq(1), any())).thenReturn(true);

        recovery.recoverPendingRequests();

        ArgumentCaptor<SeckillMessage> captor = ArgumentCaptor.forClass(SeckillMessage.class);
        verify(mqProducer).sendSeckillMessage(captor.capture());
        assertThat(captor.getValue().requestId()).isEqualTo("req-rec-1");
        verify(requestService).scheduleRetry(eq("req-rec-1"), eq(1), any());
    }

    @Test
    @DisplayName("对账窗口：订单已建 → 补落 SUCCESS，不释放占用")
    void recover_reconcileWindow_orderExists_marksSuccess() {
        when(requestService.findPendingDue(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(pending(3)));
        when(orderQueryFeignClient.findOrderIdByRequestId("req-rec-1"))
                .thenReturn(com.cloudmart.common.api.ApiResponse.ok(77L));
        when(requestService.settleSuccess("req-rec-1", 77L)).thenReturn(true);

        recovery.recoverPendingRequests();

        verify(requestService).settleSuccess("req-rec-1", 77L);
        verify(requestService, never()).settleFailed(anyString(), anyString());
        verify(requestService, never()).releaseSeat(any());
    }

    @Test
    @DisplayName("对账窗口：确认未建单 → 终态失败并释放 DB/Redis 占用")
    void recover_reconcileWindow_noOrder_failsAndReleases() {
        when(requestService.findPendingDue(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(pending(3)));
        when(orderQueryFeignClient.findOrderIdByRequestId("req-rec-1"))
                .thenReturn(com.cloudmart.common.api.ApiResponse.ok(null));
        when(requestService.settleFailed(eq("req-rec-1"), anyString())).thenReturn(true);
        when(requestService.releaseSeat(2L)).thenReturn(true);

        recovery.recoverPendingRequests();

        verify(requestService).settleFailed(eq("req-rec-1"), anyString());
        verify(requestService).releaseSeat(2L);
        verify(redisTemplate.opsForValue()).increment(anyString());
        verify(redisTemplate.opsForSet()).remove(anyString(), eq("4"));
    }

    @Test
    @DisplayName("对账查询不可用 → fail-closed 顺延（不动终态不释放）")
    void recover_reconcileUnavailable_defers() {
        when(requestService.findPendingDue(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(pending(3)));
        when(orderQueryFeignClient.findOrderIdByRequestId("req-rec-1"))
                .thenThrow(new BusinessException("ORDER_QUERY_UNAVAILABLE", "订单查询服务暂不可用"));

        recovery.recoverPendingRequests();

        verify(requestService, never()).settleSuccess(anyString(), any());
        verify(requestService, never()).settleFailed(anyString(), anyString());
        verify(requestService, never()).releaseSeat(any());
    }

    @Test
    @DisplayName("对账失败 CAS 未生效（重复调度）→ 不重复释放")
    void recover_reconcileCasLost_noDoubleRelease() {
        when(requestService.findPendingDue(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(pending(3)));
        when(orderQueryFeignClient.findOrderIdByRequestId("req-rec-1"))
                .thenReturn(com.cloudmart.common.api.ApiResponse.ok(null));
        when(requestService.settleFailed(eq("req-rec-1"), anyString())).thenReturn(false);

        recovery.recoverPendingRequests();

        verify(requestService, never()).releaseSeat(any());
    }
}
