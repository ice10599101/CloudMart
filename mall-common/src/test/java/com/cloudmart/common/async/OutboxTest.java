package com.cloudmart.common.async;

import com.cloudmart.common.async.mapper.OutboxEventMapper;
import com.cloudmart.common.async.outbox.OutboxDelivery;
import com.cloudmart.common.async.outbox.OutboxEventEntity;
import com.cloudmart.common.async.outbox.OutboxPublisher;
import com.cloudmart.common.async.outbox.OutboxRetryPolicy;
import com.cloudmart.common.async.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ASYNC-01 Outbox 单元测试：登记幂等、投递成功/失败路径、退避转死信、
 * 错误脱敏。
 */
class OutboxTest {

    private OutboxEventMapper mapper;
    private OutboxDelivery delivery;
    private OutboxPublisher publisher;
    private OutboxService service;

    private static OutboxEventEntity event(long id, String eventId, int attempts, String status) {
        OutboxEventEntity e = new OutboxEventEntity();
        e.setId(id);
        e.setEventId(eventId);
        e.setEventType("PAYMENT_SUCCESS");
        e.setSchemaVersion(1);
        e.setAggregateId("1001");
        e.setAggregateVersion(0L);
        e.setRequestId("req-1");
        e.setPayload("{\"orderId\":1001}");
        e.setStatus(status);
        e.setAttempts(attempts);
        e.setLockedBy("worker");
        e.setLockedAt(LocalDateTime.now());
        e.setLeaseVersion(7);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    @BeforeEach
    void setUp() {
        mapper = mock(OutboxEventMapper.class);
        delivery = mock(OutboxDelivery.class);
        service = new OutboxService(mapper);
        publisher = new OutboxPublisher(mapper, delivery, OutboxRetryPolicy.defaults(), 50, 60);
    }

    @Test
    @DisplayName("record 写入 PENDING 事件（与业务同事务）")
    void record_insertsEvent() {
        service.record(EventEnvelope.of("PAYMENT_SUCCESS", 1, "1001", 0, "req-1", "{\"orderId\":1001}"));

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(mapper).insertIfAbsent(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getEventType()).isEqualTo("PAYMENT_SUCCESS");
    }

    @Test
    @DisplayName("投递成功标记 SENT（回写绑定租约 owner+leaseVersion，T16 fencing）")
    void publishPending_success_marksSent() {
        when(mapper.claimBatch(anyString(), anyInt(), anyInt())).thenReturn(1);
        when(mapper.selectClaimed(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(event(1L, "evt-1", 0, "SENDING")));

        publisher.publishPending();

        verify(delivery).deliver(any(EventEnvelope.class));
        verify(mapper).markSent(eq(1L), anyString(), eq(7));
        verify(mapper, never()).markFailure(anyLong(), anyString(), anyInt(), anyInt(), anyLong(), anyString());
    }

    @Test
    @DisplayName("投递失败回 FAILED 并按退避重排，错误已脱敏")
    void publishPending_failure_recordsBackoff() {
        when(mapper.claimBatch(anyString(), anyInt(), anyInt())).thenReturn(1);
        OutboxEventEntity evt = event(2L, "evt-2", 0, "SENDING");
        when(mapper.selectClaimed(anyString(), anyInt(), anyInt())).thenReturn(List.of(evt));
        doThrow(new RuntimeException("connection refused, password=super-secret"))
                .when(delivery).deliver(any(EventEnvelope.class));

        publisher.publishPending();

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).markFailure(eq(2L), anyString(), eq(7), eq(12), anyLong(), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).doesNotContain("super-secret");
        assertThat(errorCaptor.getValue()).contains("password=***");
    }

    @Test
    @DisplayName("T16 fencing：租约被接管后旧实例迟到 SENT 回写 0 行被拒，不重复标记")
    void publishPending_staleLease_sentWriteRejected() {
        when(mapper.claimBatch(anyString(), anyInt(), anyInt())).thenReturn(1);
        when(mapper.selectClaimed(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(event(3L, "evt-3", 0, "SENDING")));
        when(mapper.markSent(anyLong(), anyString(), anyInt())).thenReturn(0);

        publisher.publishPending();

        verify(delivery).deliver(any(EventEnvelope.class));
        verify(mapper, never()).markFailure(anyLong(), anyString(), anyInt(), anyInt(), anyLong(), anyString());
    }

    @Test
    @DisplayName("退避策略：指数增长、封顶 5 分钟")
    void retryPolicy_exponentialWithCap() {
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();

        long first = policy.nextBackoffMillis(0);
        long fifth = policy.nextBackoffMillis(5);
        long twentieth = policy.nextBackoffMillis(20);

        assertThat(first).isBetween(1_000L, 2_000L);
        assertThat(fifth).isBetween(32_000L, 33_000L);
        assertThat(twentieth).isLessThanOrEqualTo(5 * 60_000L + 1_000L);
    }

    @Test
    @DisplayName("同 eventId 重复登记被 INSERT IGNORE 吸收（返回 0）")
    void record_duplicateIgnored() {
        when(mapper.insertIfAbsent(any())).thenReturn(0);

        service.record(EventEnvelope.of("PAYMENT_SUCCESS", 1, "1001", 0, "req-1", "{}"));

        verify(mapper).insertIfAbsent(any());
    }
}
