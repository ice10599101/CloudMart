package com.cloudmart.order.mq;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.async.inbox.InboxService;
import com.cloudmart.order.entity.AfterSaleCase;
import com.cloudmart.order.feign.RefundFeignClient;
import com.cloudmart.order.repository.AfterSaleCaseMapper;
import com.cloudmart.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R11：售后退款提交消费者——0 元案走订单域内部结算（不调收费渠道），正金额案才调退款 Feign。
 */
@DisplayName("AfterSaleRefundSubmitConsumer 零元/正金额分流")
class AfterSaleRefundSubmitConsumerTest {

    private InboxService inboxService;
    private AfterSaleCaseMapper caseMapper;
    private RefundFeignClient refundFeignClient;
    private OrderService orderService;
    private AfterSaleRefundSubmitConsumer consumer;

    @BeforeEach
    void setUp() {
        inboxService = mock(InboxService.class);
        caseMapper = mock(AfterSaleCaseMapper.class);
        refundFeignClient = mock(RefundFeignClient.class);
        orderService = mock(OrderService.class);
        consumer = new AfterSaleRefundSubmitConsumer(inboxService, caseMapper, refundFeignClient, orderService);
        when(inboxService.beginConsume(anyString(), any())).thenReturn(InboxService.ConsumeDecision.PROCEED);
    }

    private Map<String, Object> message(Object caseId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("caseId", caseId);
        Map<String, Object> msg = new HashMap<>();
        msg.put("eventId", "evt-" + caseId);
        msg.put("eventType", "AFTER_SALE_REFUND_SUBMIT");
        msg.put("payload", payload);
        return msg;
    }

    private AfterSaleCase approvedCase(BigDecimal amount) {
        AfterSaleCase entity = new AfterSaleCase();
        entity.setId(77L);
        entity.setCaseNo("AS77");
        entity.setOrderId(9001L);
        entity.setStatus(AfterSaleCase.STATUS_APPROVED);
        entity.setRefundNo("RFC77");
        entity.setRefundAmount(amount);
        entity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        return entity;
    }

    @Test
    @DisplayName("R11：0 元案（零元订单售后）→ 域内退款完成推进，不调用收费渠道")
    void zeroAmount_settlesWithinOrderDomain() {
        when(caseMapper.selectById(77L)).thenReturn(approvedCase(BigDecimal.ZERO));

        consumer.onMessage(message(77L));

        verify(orderService).onAfterSaleRefundCompleted("RFC77");
        verify(refundFeignClient, never()).createRefund(any());
        verify(inboxService).completeConsume(eq(AfterSaleRefundSubmitConsumer.CONSUMER), any());
    }

    @Test
    @DisplayName("BASE-01：caseId 以字符串形态到达（统一字符串序列化）→ 同键读取案件并推进退款")
    void stringCaseId_parsed() {
        when(caseMapper.selectById(77L)).thenReturn(approvedCase(BigDecimal.ZERO));

        consumer.onMessage(message("77"));

        verify(orderService).onAfterSaleRefundCompleted("RFC77");
        verify(inboxService).completeConsume(eq(AfterSaleRefundSubmitConsumer.CONSUMER), any());
    }
}
