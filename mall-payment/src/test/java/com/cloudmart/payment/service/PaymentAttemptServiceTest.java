package com.cloudmart.payment.service;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.dto.OrderInternalInfoDTO;
import com.cloudmart.payment.entity.PaymentAttempt;
import com.cloudmart.payment.feign.OrderFeignClient;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import com.cloudmart.payment.repository.PaymentNotifyLogMapper;
import com.cloudmart.common.async.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAY-01：支付尝试台账——归属/状态/金额服务端判定、单订单单活动尝试、
 * 回调验签/重放/金额不符拒绝、CAS 确认 + PAYMENT_SUCCEEDED 事件。
 */
@DisplayName("PaymentAttemptService 支付尝试")
class PaymentAttemptServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ORDER_ID = 9001L;
    private static final String SECRET = "test-secret-0123456789abcdef";

    private PaymentAttemptService service;
    private PaymentAttemptMapper attemptMapper;
    private PaymentNotifyLogMapper notifyLogMapper;
    private OutboxService outboxService;
    private OrderFeignClient orderFeignClient;
    private final com.cloudmart.payment.channel.MockChannelSigner signer =
            new com.cloudmart.payment.channel.MockChannelSigner(SECRET);

    @BeforeEach
    void setUp() {
        attemptMapper = mock(PaymentAttemptMapper.class);
        notifyLogMapper = mock(PaymentNotifyLogMapper.class);
        outboxService = mock(OutboxService.class);
        orderFeignClient = mock(OrderFeignClient.class);
        service = new PaymentAttemptService(attemptMapper, notifyLogMapper, outboxService,
                orderFeignClient, SECRET);
    }

    private OrderInternalInfoDTO payableOrder() {
        return new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PENDING_PAYMENT", new BigDecimal("88.00"));
    }

    private void stubPayableOrder() {
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(ApiResponse.ok(payableOrder()));
        when(attemptMapper.selectOne(any())).thenReturn(null);
        when(attemptMapper.insertSingleActive(eq(ORDER_ID), anyString(),
                eq("MOCK"), eq(new BigDecimal("88.00")), eq("CNY"))).thenReturn(1);
        when(attemptMapper.findByMerchantPaymentNo(anyString())).thenAnswer(inv -> {
            PaymentAttempt a = new PaymentAttempt();
            a.setId(1L);
            a.setOrderId(ORDER_ID);
            a.setMerchantPaymentNo(inv.getArgument(0));
            a.setChannel("MOCK");
            a.setAmount(new BigDecimal("88.00"));
            a.setCurrency("CNY");
            a.setStatus("PENDING");
            return a;
        });
    }

    @Test
    @DisplayName("创建尝试：金额取服务端（客户端金额不参与），归属校验通过")
    void createAttempt_serverSideAmount() {
        stubPayableOrder();

        var attempt = service.createAttempt(USER_ID, ORDER_ID, "MOCK");

        assertThat(attempt.getAmount()).isEqualByComparingTo(new BigDecimal("88.00"));
        verify(attemptMapper).insertSingleActive(eq(ORDER_ID), anyString(),
                eq("MOCK"), eq(new BigDecimal("88.00")), eq("CNY"));
    }

    @Test
    @DisplayName("他人订单拒绝创建（归属校验）")
    void createAttempt_othersOrder_rejected() {
        when(orderFeignClient.getOrderInfo(ORDER_ID))
                .thenReturn(ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, 999L, "PENDING_PAYMENT", new BigDecimal("88.00"))));

        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "MOCK"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_FORBIDDEN");
        verify(attemptMapper, never()).insertSingleActive(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("非待支付订单拒绝创建（可支付状态服务端判定）")
    void createAttempt_notPayable_rejected() {
        when(orderFeignClient.getOrderInfo(ORDER_ID))
                .thenReturn(ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "CANCELLED", new BigDecimal("88.00"))));

        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "MOCK"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_STATUS_ERROR");
    }

    @Test
    @DisplayName("回调：有效签名 + 金额一致 → CAS 确认并发布 PAYMENT_SUCCEEDED")
    void handleNotify_validSignature_confirmed() {
        var notification = signer.sign("MP001", "88.00", "TXN001");
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(1L);
        attempt.setOrderId(ORDER_ID);
        attempt.setMerchantPaymentNo("MP001");
        attempt.setAmount(new BigDecimal("88.00"));
        attempt.setStatus("PENDING");
        when(attemptMapper.findByMerchantPaymentNo("MP001")).thenReturn(attempt);
        when(attemptMapper.confirmSuccess("MP001", new BigDecimal("88.00"), "TXN001")).thenReturn(1);
        when(notifyLogMapper.record(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);

        String result = service.handleMockNotify("MP001", "88.00",
                notification.providerTxnNo(), notification.notificationId(), notification.signature());

        assertThat(result).isEqualTo("SUCCESS");
        ArgumentCaptor<com.cloudmart.common.async.EventEnvelope> captor =
                ArgumentCaptor.forClass(com.cloudmart.common.async.EventEnvelope.class);
        verify(outboxService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("PAYMENT_SUCCEEDED");
        assertThat(captor.getValue().payload()).contains("MP001");
    }

    @Test
    @DisplayName("回调：签名篡改 → REJECTED 且不入账、不发事件")
    void handleNotify_tamperedSignature_rejected() {
        var notification = signer.sign("MP001", "88.00", "TXN001");
        when(notifyLogMapper.record(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);

        String result = service.handleMockNotify("MP001", "88.00",
                notification.providerTxnNo(), notification.notificationId(), "bad" + notification.signature());

        assertThat(result).isEqualTo("REJECTED");
        verify(attemptMapper, never()).confirmSuccess(org.mockito.ArgumentMatchers.anyString(), any(), anyString());
        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("回调：重放通知（同 notificationId 已处理）→ DUPLICATE 不二次入账")
    void handleNotify_replay_duplicate() {
        var notification = signer.sign("MP001", "88.00", "TXN001");
        when(attemptMapper.findByMerchantPaymentNo("MP001")).thenReturn(new PaymentAttempt());
        // 通知登记返回 0 行 = 重放
        when(notifyLogMapper.record(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), org.mockito.ArgumentMatchers.any())).thenReturn(0);

        String result = service.handleMockNotify("MP001", "88.00",
                notification.providerTxnNo(), notification.notificationId(), notification.signature());

        assertThat(result).isEqualTo("DUPLICATE");
        verify(attemptMapper, never()).confirmSuccess(org.mockito.ArgumentMatchers.anyString(), any(), anyString());
        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("回调：金额不符 → REJECTED（渠道金额不改变服务端应付）")
    void handleNotify_amountMismatch_rejected() {
        var notification = signer.sign("MP001", "0.01", "TXN001");
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(1L);
        attempt.setOrderId(ORDER_ID);
        attempt.setMerchantPaymentNo("MP001");
        attempt.setAmount(new BigDecimal("88.00"));
        attempt.setStatus("PENDING");
        when(attemptMapper.findByMerchantPaymentNo("MP001")).thenReturn(attempt);
        when(notifyLogMapper.record(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);

        // 用 0.01 的有效签名（金额与台账 88.00 不符）
        String result = service.handleMockNotify("MP001", "0.01",
                notification.providerTxnNo(), notification.notificationId(), notification.signature());

        assertThat(result).isEqualTo("REJECTED");
        verify(attemptMapper, never()).confirmSuccess(org.mockito.ArgumentMatchers.anyString(), any(), anyString());
    }
}
