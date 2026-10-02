package com.cloudmart.seckill.mq;

import com.cloudmart.common.async.inbox.InboxService;
import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.service.SeckillRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T09 结果回写消费者测试：Inbox 幂等——100 次重复消息一份结果；
 * 成功落终态，失败落终态并释放 DB/Redis 占用（仅 CAS 生效方释放）。
 */
class SeckillResultConsumerTest {

    private InboxService inboxService;
    private SeckillRequestService requestService;
    private StringRedisTemplate redisTemplate;
    private SeckillResultConsumer consumer;

    private static final String REQUEST_ID = "req-abc-000001";

    @BeforeEach
    void setUp() {
        inboxService = mock(InboxService.class);
        requestService = mock(SeckillRequestService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(true);
        consumer = new SeckillResultConsumer(inboxService, requestService, redisTemplate);

        SeckillRequest request = new SeckillRequest();
        request.setRequestId(REQUEST_ID);
        request.setActivityId(1L);
        request.setProductId(2L);
        request.setUserId(3L);
        request.setStatus("PENDING");
        lenient().when(requestService.findByRequestId(REQUEST_ID)).thenReturn(request);
        lenient().when(inboxService.beginConsume(anyString(), any()))
                .thenReturn(InboxService.ConsumeDecision.PROCEED);
    }

    private Map<String, Object> event(boolean success, Long orderId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", REQUEST_ID);
        payload.put("success", success);
        if (orderId != null) {
            payload.put("orderId", orderId);
        }
        Map<String, Object> message = new HashMap<>();
        message.put("eventId", "evt-1");
        message.put("eventType", "SECKILL_RESULT");
        message.put("payload", payload);
        return message;
    }

    @Test
    @DisplayName("LC03：缺 eventId 的旧形状事件拒绝消费（不进 Inbox）")
    void onMessage_missingEventId_rejected() {
        Map<String, Object> message = event(true, 1L);
        message.remove("eventId");

        consumer.onMessage(message);

        verify(inboxService, never()).beginConsume(anyString(), any());
    }

    @Test
    @DisplayName("Inbox SKIP（已消费/他实例处理中）→ 不重复结算")
    void onMessage_inboxSkip_noSettle() {
        when(inboxService.beginConsume(anyString(), any()))
                .thenReturn(InboxService.ConsumeDecision.SKIP);

        consumer.onMessage(event(true, 1L));

        verify(requestService, never()).settleSuccess(anyString(), any());
    }

    @Test
    @DisplayName("成功：CAS 落 SUCCESS 并完成 Inbox")
    void onMessage_success_settlesAndCompletes() {
        when(requestService.settleSuccess(REQUEST_ID, 66L)).thenReturn(true);

        consumer.onMessage(event(true, 66L));

        verify(requestService).settleSuccess(REQUEST_ID, 66L);
        verify(requestService, never()).settleFailed(anyString(), anyString());
        verify(inboxService).completeConsume(anyString(), any());
    }

    @Test
    @DisplayName("失败：CAS 落 FAILED（生效方）释放 DB/Redis 占用")
    void onMessage_failed_settlesAndReleases() {
        when(requestService.settleFailed(eq(REQUEST_ID), anyString())).thenReturn(true);
        when(requestService.releaseSeat(2L)).thenReturn(true);

        consumer.onMessage(event(false, null));

        verify(requestService).settleFailed(eq(REQUEST_ID), anyString());
        verify(requestService).releaseSeat(2L);
        verify(redisTemplate.opsForValue()).increment(anyString());
        verify(redisTemplate.opsForSet()).remove(anyString(), eq("3"));
        verify(inboxService).completeConsume(anyString(), any());
    }

    @Test
    @DisplayName("失败：CAS 未生效（重复消费）→ 不重复释放")
    void onMessage_failedCasLost_noDoubleRelease() {
        when(requestService.settleFailed(eq(REQUEST_ID), anyString())).thenReturn(false);

        consumer.onMessage(event(false, null));

        verify(requestService, never()).releaseSeat(any());
    }

    @Test
    @DisplayName("业务异常：failConsume 留痕后重抛（事务回滚 + MQ 重投）")
    void onMessage_businessError_failsAndRethrows() {
        when(requestService.settleSuccess(REQUEST_ID, 66L))
                .thenThrow(new IllegalStateException("db down"));
        doAnswer(inv -> null).when(inboxService).failConsume(anyString(), any(), anyString());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.onMessage(event(true, 66L)))
                .isInstanceOf(IllegalStateException.class);

        verify(inboxService).failConsume(anyString(), any(), anyString());
        verify(inboxService, never()).completeConsume(anyString(), any());
    }
}
