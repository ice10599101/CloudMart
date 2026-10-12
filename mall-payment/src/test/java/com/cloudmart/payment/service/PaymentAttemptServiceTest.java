package com.cloudmart.payment.service;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.channel.ChannelRegistry;
import com.cloudmart.payment.channel.PaymentChannelAdapter;
import com.cloudmart.payment.channel.PaymentScene;
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
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAY-01/R09：支付尝试台账——归属/状态/金额服务端判定、requestKey 稳定幂等、
 * 同单活动尝试唯一（命名锁+唯一索引）、换渠道关闭旧尝试、returnUrl 白名单、
 * 回调验签/重放/金额不符拒绝、CAS 确认 + PAYMENT_SUCCEEDED 事件。
 */
@DisplayName("PaymentAttemptService 支付尝试")
class PaymentAttemptServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ORDER_ID = 9001L;
    private static final String SECRET = "test-secret-0123456789abcdef";
    private static final String REQUEST_KEY = "rk-0001";

    private PaymentAttemptService service;
    private PaymentAttemptMapper attemptMapper;
    private PaymentNotifyLogMapper notifyLogMapper;
    private OutboxService outboxService;
    private OrderFeignClient orderFeignClient;
    private final com.cloudmart.payment.channel.MockChannelSigner signer =
            new com.cloudmart.payment.channel.MockChannelSigner(SECRET);

    /** 最小可用适配器桩（白名单内真实渠道的测试替身；不承担任何真实收单） */
    private static PaymentChannelAdapter adapterStub(String channel) {
        return new PaymentChannelAdapter() {
            @Override
            public String channel() {
                return channel;
            }

            @Override
            public ChannelPaymentResponse createPayment(String merchantPaymentNo, BigDecimal amount,
                                                        String subject, String scene, String returnUrl) {
                return ChannelPaymentResponse.ok("TX-" + merchantPaymentNo, "{}");
            }

            @Override
            public ChannelStatusResponse queryPayment(String merchantPaymentNo) {
                return new ChannelStatusResponse(merchantPaymentNo, null, "PROCESSING", null, null);
            }

            @Override
            public ChannelRefundResponse createRefund(String merchantPaymentNo, String refundNo,
                                                      BigDecimal refundAmount, String reason) {
                return ChannelRefundResponse.reject("UNSUPPORTED", "stub");
            }

            @Override
            public ChannelStatusResponse queryRefund(String refundNo) {
                return new ChannelStatusResponse(null, null, "UNKNOWN", null, null);
            }

            @Override
            public boolean isConfigured() {
                return true;
            }
        };
    }

    private PaymentAttemptService buildService(ChannelRegistry registry, String returnUrlPrefixesCsv) {
        return new PaymentAttemptService(attemptMapper, notifyLogMapper, outboxService,
                orderFeignClient, registry,
                new PaymentChannelQueryService(registry,
                        new com.cloudmart.payment.channel.PaymentChannelCatalog(), ""),
                SECRET, returnUrlPrefixesCsv);
    }

    private ChannelRegistry mockOnlyRegistry() {
        return new ChannelRegistry(List.of(), true, mock(Environment.class));
    }

    @BeforeEach
    void setUp() {
        attemptMapper = mock(PaymentAttemptMapper.class);
        notifyLogMapper = mock(PaymentNotifyLogMapper.class);
        outboxService = mock(OutboxService.class);
        orderFeignClient = mock(OrderFeignClient.class);
        service = buildService(mockOnlyRegistry(), "");
    }

    private OrderInternalInfoDTO payableOrder() {
        return new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PENDING_PAYMENT", new BigDecimal("88.00"));
    }

    private PaymentAttempt pendingAttempt(Long id, String channel, String scene) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(id);
        attempt.setOrderId(ORDER_ID);
        attempt.setMerchantPaymentNo("MP" + id);
        attempt.setChannel(channel);
        attempt.setScene(scene);
        attempt.setRequestKey("old-key-" + id);
        attempt.setAmount(new BigDecimal("88.00"));
        attempt.setCurrency("CNY");
        attempt.setStatus("PENDING");
        return attempt;
    }

    private void stubFreshLockAndInsert() {
        when(attemptMapper.acquireOrderLock(ORDER_ID, 5)).thenReturn(1);
        when(attemptMapper.releaseOrderLock(ORDER_ID)).thenReturn(1);
        when(attemptMapper.findByRequestKey(anyString())).thenReturn(null);
        when(attemptMapper.selectLatestByOrderForUpdate(ORDER_ID)).thenReturn(null);
        when(attemptMapper.insertAttempt(anyLong(), anyString(), eq("MOCK"), eq("H5"),
                eq(REQUEST_KEY), eq(new BigDecimal("88.00")), eq("CNY"))).thenReturn(1);
        when(attemptMapper.findByMerchantPaymentNo(anyString())).thenAnswer(inv -> {
            PaymentAttempt attempt = pendingAttempt(1L, "MOCK", "H5");
            attempt.setMerchantPaymentNo(inv.getArgument(0));
            attempt.setRequestKey(REQUEST_KEY);
            return attempt;
        });
    }

    @Test
    @DisplayName("创建尝试：金额取服务端（客户端金额不参与），场景与幂等键落库")
    void createAttempt_serverSideAmount() {
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(ApiResponse.ok(payableOrder()));
        stubFreshLockAndInsert();

        var result = service.createAttempt(USER_ID, ORDER_ID, "MOCK", PaymentScene.H5, REQUEST_KEY, null);

        assertThat(result.attempt().getAmount()).isEqualByComparingTo(new BigDecimal("88.00"));
        assertThat(result.replacedPrevious()).isFalse();
        verify(attemptMapper).insertAttempt(eq(ORDER_ID), anyString(), eq("MOCK"), eq("H5"),
                eq(REQUEST_KEY), eq(new BigDecimal("88.00")), eq("CNY"));
        verify(attemptMapper).acquireOrderLock(ORDER_ID, 5);
        verify(attemptMapper).releaseOrderLock(ORDER_ID);
    }

    @Test
    @DisplayName("他人订单拒绝创建（归属校验）")
    void createAttempt_othersOrder_rejected() {
        when(orderFeignClient.getOrderInfo(ORDER_ID))
                .thenReturn(ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, 999L, "PENDING_PAYMENT", new BigDecimal("88.00"))));

        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "MOCK", PaymentScene.H5, REQUEST_KEY, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_FORBIDDEN");
        verify(attemptMapper, never()).insertAttempt(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString());
    }

    @Test
    @DisplayName("非待支付订单拒绝创建（可支付状态服务端判定）")
    void createAttempt_notPayable_rejected() {
        when(orderFeignClient.getOrderInfo(ORDER_ID))
                .thenReturn(ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "CANCELLED", new BigDecimal("88.00"))));

        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "MOCK", PaymentScene.H5, REQUEST_KEY, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_STATUS_ERROR");
    }

    @Test
    @DisplayName("T07：未知渠道在创建尝试前拒绝（不产生\"看似可支付\"记录）")
    void createAttempt_unknownChannel_rejected() {
        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "ALIPAY", PaymentScene.H5, REQUEST_KEY, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_CHANNEL_UNAVAILABLE");
        verify(attemptMapper, never()).insertAttempt(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(BigDecimal.class), anyString());
        verify(attemptMapper, never()).acquireOrderLock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("R09：渠道不支持该场景（ALIPAY 不进 MINIAPP）→ PAYMENT_SCENE_UNSUPPORTED")
    void createAttempt_sceneUnsupported_rejected() {
        ChannelRegistry registry = new ChannelRegistry(
                List.of(adapterStub("ALIPAY")), false, mock(Environment.class));
        service = buildService(registry, "");

        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "ALIPAY", PaymentScene.MINIAPP, REQUEST_KEY, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_SCENE_UNSUPPORTED");
        verify(attemptMapper, never()).acquireOrderLock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("R09：requestKey 缺失/超长拒绝")
    void createAttempt_blankOrLongKey_rejected() {
        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "MOCK", PaymentScene.H5, "  ", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
        String longKey = "k".repeat(65);
        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "MOCK", PaymentScene.H5, longKey, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("R09：returnUrl 默认全拒绝（未配置白名单时 fail-closed）")
    void createAttempt_returnUrlNotWhitelisted_rejected() {
        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "MOCK",
                PaymentScene.H5, REQUEST_KEY, "https://evil.example/pay"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_RETURN_URL_FORBIDDEN");
        verify(attemptMapper, never()).acquireOrderLock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("R09：returnUrl 命中前缀白名单放行")
    void createAttempt_returnUrlWhitelisted_accepted() {
        ChannelRegistry registry = mockOnlyRegistry();
        service = buildService(registry, "https://www.cloudmart.example/pay/");
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(ApiResponse.ok(payableOrder()));
        when(attemptMapper.acquireOrderLock(ORDER_ID, 5)).thenReturn(1);
        when(attemptMapper.releaseOrderLock(ORDER_ID)).thenReturn(1);
        when(attemptMapper.findByRequestKey(anyString())).thenReturn(null);
        when(attemptMapper.selectLatestByOrderForUpdate(ORDER_ID)).thenReturn(null);
        when(attemptMapper.insertAttempt(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString())).thenReturn(1);
        when(attemptMapper.findByMerchantPaymentNo(anyString())).thenAnswer(inv -> {
            PaymentAttempt attempt = pendingAttempt(1L, "MOCK", "H5");
            attempt.setMerchantPaymentNo(inv.getArgument(0));
            return attempt;
        });

        var result = service.createAttempt(USER_ID, ORDER_ID, "MOCK", PaymentScene.H5,
                REQUEST_KEY, "https://www.cloudmart.example/pay/return?order=1");

        assertThat(result.attempt().getChannel()).isEqualTo("MOCK");
    }

    @Test
    @DisplayName("R09：命名锁获取失败 → PAYMENT_ATTEMPT_BUSY（不无限等待）")
    void createAttempt_lockBusy_rejected() {
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(ApiResponse.ok(payableOrder()));
        when(attemptMapper.acquireOrderLock(ORDER_ID, 5)).thenReturn(0);

        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "MOCK", PaymentScene.H5, REQUEST_KEY, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_ATTEMPT_BUSY");
        verify(attemptMapper, never()).insertAttempt(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("R09：同 requestKey 同参重复提交 → 稳定返回原尝试，不新建")
    void createAttempt_sameKey_reusesAttempt() {
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(ApiResponse.ok(payableOrder()));
        when(attemptMapper.acquireOrderLock(ORDER_ID, 5)).thenReturn(1);
        when(attemptMapper.releaseOrderLock(ORDER_ID)).thenReturn(1);
        PaymentAttempt keyed = pendingAttempt(7L, "MOCK", "H5");
        keyed.setRequestKey(REQUEST_KEY);
        when(attemptMapper.findByRequestKey(REQUEST_KEY)).thenReturn(keyed);

        var result = service.createAttempt(USER_ID, ORDER_ID, "MOCK", PaymentScene.H5, REQUEST_KEY, null);

        assertThat(result.attempt().getId()).isEqualTo(7L);
        assertThat(result.replacedPrevious()).isFalse();
        verify(attemptMapper, never()).insertAttempt(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("R09：同 requestKey 异参（换订单）→ IDEMPOTENCY_KEY_REUSED")
    void createAttempt_sameKeyDifferentParams_conflict() {
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(ApiResponse.ok(payableOrder()));
        when(attemptMapper.acquireOrderLock(ORDER_ID, 5)).thenReturn(1);
        when(attemptMapper.releaseOrderLock(ORDER_ID)).thenReturn(1);
        PaymentAttempt keyed = pendingAttempt(8L, "MOCK", "H5");
        keyed.setOrderId(9999L);
        when(attemptMapper.findByRequestKey(REQUEST_KEY)).thenReturn(keyed);

        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "MOCK", PaymentScene.H5, REQUEST_KEY, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_REUSED");
        verify(attemptMapper, never()).insertAttempt(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("R09：活动尝试同渠道同场景 → 复用，不换号")
    void createAttempt_activeSameChannel_reused() {
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(ApiResponse.ok(payableOrder()));
        when(attemptMapper.acquireOrderLock(ORDER_ID, 5)).thenReturn(1);
        when(attemptMapper.releaseOrderLock(ORDER_ID)).thenReturn(1);
        when(attemptMapper.findByRequestKey(anyString())).thenReturn(null);
        when(attemptMapper.selectLatestByOrderForUpdate(ORDER_ID))
                .thenReturn(pendingAttempt(3L, "MOCK", "H5"));

        var result = service.createAttempt(USER_ID, ORDER_ID, "MOCK", PaymentScene.H5, "new-key", null);

        assertThat(result.attempt().getId()).isEqualTo(3L);
        verify(attemptMapper, never()).closeActiveById(anyLong());
        verify(attemptMapper, never()).insertAttempt(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("R09：换渠道 → CAS 关闭旧尝试、新建并显式 replacedPrevious/previousAttemptId")
    void createAttempt_channelSwitch_closesPrevious() {
        ChannelRegistry registry = new ChannelRegistry(
                List.of(adapterStub("ALIPAY"), adapterStub("WECHAT")), false, mock(Environment.class));
        service = buildService(registry, "");
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(ApiResponse.ok(payableOrder()));
        when(attemptMapper.acquireOrderLock(ORDER_ID, 5)).thenReturn(1);
        when(attemptMapper.releaseOrderLock(ORDER_ID)).thenReturn(1);
        when(attemptMapper.findByRequestKey(anyString())).thenReturn(null);
        when(attemptMapper.selectLatestByOrderForUpdate(ORDER_ID))
                .thenReturn(pendingAttempt(5L, "ALIPAY", "H5"));
        when(attemptMapper.closeActiveById(5L)).thenReturn(1);
        when(attemptMapper.insertAttempt(anyLong(), anyString(), eq("WECHAT"), eq("H5"),
                eq(REQUEST_KEY), eq(new BigDecimal("88.00")), eq("CNY"))).thenReturn(1);
        when(attemptMapper.findByMerchantPaymentNo(anyString())).thenAnswer(inv -> {
            PaymentAttempt created = pendingAttempt(6L, "WECHAT", "H5");
            created.setMerchantPaymentNo(inv.getArgument(0));
            created.setRequestKey(REQUEST_KEY);
            return created;
        });

        var result = service.createAttempt(USER_ID, ORDER_ID, "WECHAT", PaymentScene.H5, REQUEST_KEY, null);

        assertThat(result.attempt().getId()).isEqualTo(6L);
        assertThat(result.attempt().getChannel()).isEqualTo("WECHAT");
        assertThat(result.replacedPrevious()).isTrue();
        assertThat(result.previousAttemptId()).isEqualTo(5L);
        verify(attemptMapper).closeActiveById(5L);
    }

    @Test
    @DisplayName("R09：换渠道时旧尝试已被并发关闭（CAS 0 行）→ PAYMENT_ATTEMPT_BUSY")
    void createAttempt_channelSwitchCloseRace_busy() {
        ChannelRegistry registry = new ChannelRegistry(
                List.of(adapterStub("ALIPAY"), adapterStub("WECHAT")), false, mock(Environment.class));
        service = buildService(registry, "");
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(ApiResponse.ok(payableOrder()));
        when(attemptMapper.acquireOrderLock(ORDER_ID, 5)).thenReturn(1);
        when(attemptMapper.releaseOrderLock(ORDER_ID)).thenReturn(1);
        when(attemptMapper.findByRequestKey(anyString())).thenReturn(null);
        when(attemptMapper.selectLatestByOrderForUpdate(ORDER_ID))
                .thenReturn(pendingAttempt(5L, "ALIPAY", "H5"));
        when(attemptMapper.closeActiveById(5L)).thenReturn(0);

        assertThatThrownBy(() -> service.createAttempt(USER_ID, ORDER_ID, "WECHAT", PaymentScene.H5, REQUEST_KEY, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_ATTEMPT_BUSY");
        verify(attemptMapper, never()).insertAttempt(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(BigDecimal.class), anyString());
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
        verify(attemptMapper, never()).confirmSuccess(anyString(), any(), anyString());
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
        verify(attemptMapper, never()).confirmSuccess(anyString(), any(), anyString());
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
        verify(attemptMapper, never()).confirmSuccess(anyString(), any(), anyString());
    }
}
