package com.cloudmart.common.async;

import com.cloudmart.common.async.inbox.InboxRecordEntity;
import com.cloudmart.common.async.inbox.InboxService;
import com.cloudmart.common.async.mapper.InboxRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ASYNC-01 Inbox 单元测试：首次消费 PROCEED、已处理 SKIP、失败重试接管、
 * 他实例处理中 SKIP。
 */
class InboxServiceTest {

    private InboxRecordMapper mapper;
    private InboxService inbox;

    private static final EventEnvelope EVENT =
            EventEnvelope.of("PAYMENT_SUCCESS", 1, "1001", 0, "req-1", "{}");
    private static final String CONSUMER = "order-payment-result";

    @BeforeEach
    void setUp() {
        mapper = mock(InboxRecordMapper.class);
        inbox = new InboxService(mapper, 300);
    }

    @Test
    @DisplayName("首次消费：插入成功 → PROCEED")
    void firstConsume_proceeds() {
        when(mapper.insertProcessing(anyString(), anyString(), anyString(), anyString())).thenReturn(1);

        var decision = inbox.beginConsume(CONSUMER, EVENT);

        assertThat(decision).isEqualTo(InboxService.ConsumeDecision.PROCEED);
    }

    @Test
    @DisplayName("已处理完成的事件 → SKIP（容忍 MQ 重发）")
    void processedEvent_skipped() {
        when(mapper.insertProcessing(anyString(), anyString(), anyString(), anyString())).thenReturn(0);
        InboxRecordEntity existing = new InboxRecordEntity();
        existing.setStatus("PROCESSED");
        when(mapper.find(CONSUMER, EVENT.eventId())).thenReturn(existing);

        var decision = inbox.beginConsume(CONSUMER, EVENT);

        assertThat(decision).isEqualTo(InboxService.ConsumeDecision.SKIP);
        verify(mapper, never()).takeOver(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("上次失败的事件 → 接管重试 PROCEED")
    void failedEvent_takeOverProceeds() {
        when(mapper.insertProcessing(anyString(), anyString(), anyString(), anyString())).thenReturn(0);
        InboxRecordEntity existing = new InboxRecordEntity();
        existing.setStatus("FAILED");
        existing.setAttempts(1);
        when(mapper.find(CONSUMER, EVENT.eventId())).thenReturn(existing);
        when(mapper.takeOver(anyString(), anyString(), anyInt())).thenReturn(1);

        var decision = inbox.beginConsume(CONSUMER, EVENT);

        assertThat(decision).isEqualTo(InboxService.ConsumeDecision.PROCEED);
    }

    @Test
    @DisplayName("他实例处理中（锁未过期）→ SKIP")
    void processingByOtherInstance_skipped() {
        when(mapper.insertProcessing(anyString(), anyString(), anyString(), anyString())).thenReturn(0);
        InboxRecordEntity existing = new InboxRecordEntity();
        existing.setStatus("PROCESSING");
        when(mapper.find(CONSUMER, EVENT.eventId())).thenReturn(existing);
        when(mapper.takeOver(anyString(), anyString(), anyInt())).thenReturn(0);

        var decision = inbox.beginConsume(CONSUMER, EVENT);

        assertThat(decision).isEqualTo(InboxService.ConsumeDecision.SKIP);
    }

    @Test
    @DisplayName("完成/失败标记落到正确的 (consumer, eventId)")
    void completeAndFail_markedCorrectly() {
        inbox.completeConsume(CONSUMER, EVENT);
        verify(mapper).markProcessed(CONSUMER, EVENT.eventId());

        inbox.failConsume(CONSUMER, EVENT, "downstream timeout");
        verify(mapper).markFailed(CONSUMER, EVENT.eventId(), "downstream timeout");
    }
}
