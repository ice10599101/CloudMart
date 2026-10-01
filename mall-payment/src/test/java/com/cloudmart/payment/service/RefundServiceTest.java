package com.cloudmart.payment.service;

import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.entity.PaymentAttempt;
import com.cloudmart.payment.entity.RefundOrder;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import com.cloudmart.payment.repository.RefundOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T02 退款单语义（QA06/QA07 单测层）：refundNo 幂等重放/异参冲突、超退核算、
 * 审批与渠道确认分离、MOCK 同构确认 + REFUND_SUCCEEDED Outbox 同事务、
 * 真实渠道未接入明确失败。唯一键/行锁行为由真实 MySQL 集成测试证明（NOT RUN→CI）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("T02 RefundService 退款单")
class RefundServiceTest {

    private static final Long ORDER_ID = 9001L;
    private static final Long ATTEMPT_ID = 71L;
    private static final BigDecimal PAID = new BigDecimal("88.00");

    @Mock
    private RefundOrderMapper refundMapper;
    @Mock
    private PaymentAttemptMapper attemptMapper;
    @Mock
    private OutboxService outboxService;

    private RefundService service;

    @BeforeEach
    void setUp() {
        service = new RefundService(refundMapper, attemptMapper, outboxService);
        lenient().when(attemptMapper.selectByIdForUpdate(ATTEMPT_ID)).thenReturn(successAttempt("MOCK"));
        lenient().when(attemptMapper.selectLatestSuccessByOrderForUpdate(ORDER_ID)).thenReturn(successAttempt("MOCK"));
        lenient().when(refundMapper.sumActiveRefundAmount(ATTEMPT_ID)).thenReturn(BigDecimal.ZERO);
        lenient().when(refundMapper.insertRefund(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        lenient().when(refundMapper.markProcessing(anyString())).thenReturn(1);
        lenient().when(refundMapper.markSucceeded(anyString(), anyString())).thenReturn(1);
        // 首次调用（幂等预检）返回 null 走新建；结束回读返回持久化结果
        lenient().when(refundMapper.findByRefundNo(anyString())).thenReturn(null)
                .thenAnswer(inv -> persisted(inv.getArgument(0)));
    }

    private PaymentAttempt successAttempt(String channel) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(ATTEMPT_ID);
        attempt.setOrderId(ORDER_ID);
        attempt.setMerchantPaymentNo("MP1");
        attempt.setChannel(channel);
        attempt.setStatus("SUCCESS");
        attempt.setAmount(PAID);
        attempt.setCurrency("CNY");
        return attempt;
    }

    private RefundOrder persisted(String refundNo) {
        RefundOrder refund = new RefundOrder();
        refund.setId(1L);
        refund.setRefundNo(refundNo);
        refund.setOrderId(ORDER_ID);
        refund.setPaymentAttemptId(ATTEMPT_ID);
        refund.setRefundAmount(PAID);
        refund.setCurrency("CNY");
        refund.setStatus("SUCCEEDED");
        refund.setProviderRefundNo("MOCKRFND1");
        return refund;
    }

    @Test
    @DisplayName("MOCK 渠道全额退款：REQUESTED→PROCESSING→SUCCEEDED 同构推进 + REFUND_SUCCEEDED Outbox")
    void createAndSubmit_mockChannel_succeedsWithEvent() {
        RefundOrder result = service.createAndSubmit("RF9001", ORDER_ID, null,
                PAID, "CNY", "ORDER_REFUND");

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getProviderRefundNo()).startsWith("MOCKRFND");
        verify(refundMapper).markProcessing("RF9001");
        verify(refundMapper).markSucceeded(eq("RF9001"), anyString());
        ArgumentCaptor<com.cloudmart.common.async.EventEnvelope> captor =
                ArgumentCaptor.forClass(com.cloudmart.common.async.EventEnvelope.class);
        verify(outboxService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("REFUND_SUCCEEDED");
        assertThat(captor.getValue().payload()).contains("\"refundNo\":\"RF9001\"");
    }

    @Test
    @DisplayName("refundNo 幂等重放：同号同参返回原结果，不再提交渠道/重复发事件")
    void createAndSubmit_sameRefundNo_replays() {
        when(refundMapper.findByRefundNo("RF9001")).thenReturn(persisted("RF9001"));

        RefundOrder result = service.createAndSubmit("RF9001", ORDER_ID, ATTEMPT_ID,
                PAID, "CNY", "ORDER_REFUND");

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        verify(refundMapper, never()).insertRefund(anyString(), any(), any(), any(), any(), any());
        verify(refundMapper, never()).markSucceeded(anyString(), anyString());
        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("同号异参：REFUND_NO_CONFLICT（幂等冲突）")
    void createAndSubmit_sameRefundNoDifferentAmount_conflict() {
        when(refundMapper.findByRefundNo("RF9001")).thenReturn(persisted("RF9001"));

        assertThatThrownBy(() -> service.createAndSubmit("RF9001", ORDER_ID, ATTEMPT_ID,
                new BigDecimal("10.00"), "CNY", "ORDER_REFUND"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo("REFUND_NO_CONFLICT"));
    }

    @Test
    @DisplayName("QA07 超退：累计在途/已退 + 本次 > 实收 → REFUND_AMOUNT_EXCEEDS，不创建退款单")
    void createAndSubmit_amountExceedsPaid_rejected() {
        when(refundMapper.sumActiveRefundAmount(ATTEMPT_ID)).thenReturn(new BigDecimal("80.00"));

        assertThatThrownBy(() -> service.createAndSubmit("RF9002", ORDER_ID, ATTEMPT_ID,
                new BigDecimal("20.00"), "CNY", "ORDER_REFUND"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo("REFUND_AMOUNT_EXCEEDS"));
        verify(refundMapper, never()).insertRefund(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("QA06 原支付不存在/未成功：拒绝退款，不推进任何状态")
    void createAndSubmit_noSuccessAttempt_rejected() {
        when(attemptMapper.selectLatestSuccessByOrderForUpdate(ORDER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.createAndSubmit("RF9003", ORDER_ID, null,
                PAID, "CNY", "ORDER_REFUND"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo("REFUND_ATTEMPT_INVALID"));
        verify(refundMapper, never()).insertRefund(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("真实渠道未接入：REFUND_CHANNEL_UNAVAILABLE 明确失败，不产生退款记录")
    void createAndSubmit_realChannelNotWired_failsExplicitly() {
        when(attemptMapper.selectLatestSuccessByOrderForUpdate(ORDER_ID)).thenReturn(successAttempt("ALIPAY"));

        assertThatThrownBy(() -> service.createAndSubmit("RF9004", ORDER_ID, null,
                PAID, "CNY", "ORDER_REFUND"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo("REFUND_CHANNEL_UNAVAILABLE"));
        verify(refundMapper, never()).insertRefund(anyString(), any(), any(), any(), any(), any());
        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("币种不匹配：REFUND_CURRENCY_MISMATCH")
    void createAndSubmit_currencyMismatch_rejected() {
        assertThatThrownBy(() -> service.createAndSubmit("RF9005", ORDER_ID, null,
                PAID, "USD", "ORDER_REFUND"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo("REFUND_CURRENCY_MISMATCH"));
    }
}
