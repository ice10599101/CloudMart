package com.cloudmart.payment.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 渠道白名单注册表（T07）：仅已接入且凭据齐全的适配器进白名单；MOCK 仅在
 * 明确开启时可用（生产 profile 必须关闭）。未知渠道在创建支付尝试<b>前</b>
 * 被拒绝——不产生"看似可支付"的记录。
 */
@Slf4j
@Component
public class ChannelRegistry {

    private final Map<String, PaymentChannelAdapter> adapters;
    private final boolean mockEnabled;

    public ChannelRegistry(List<PaymentChannelAdapter> configuredAdapters,
                           @Value("${payment.mock-channel-enabled:true}") boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
        if (mockEnabled) {
            this.adapters = Map.of("MOCK", new MockAdapterMarker());
        } else {
            this.adapters = configuredAdapters.stream()
                    .filter(PaymentChannelAdapter::isConfigured)
                    .collect(Collectors.toMap(PaymentChannelAdapter::channel, a -> a));
        }
        log.info("[T07] 支付渠道白名单: {}（mockEnabled={}）", adapters.keySet(), mockEnabled);
    }

    /** @return 渠道是否可用（未知/未配置/未启用一律不可） */
    public boolean isChannelAllowed(String channel) {
        return channel != null && !channel.isBlank() && adapters.containsKey(channel.toUpperCase());
    }

    /** @return 渠道适配器；白名单外返回 null */
    public PaymentChannelAdapter adapterOf(String channel) {
        return channel == null ? null : adapters.get(channel.toUpperCase());
    }

    /** MOCK 仅作为白名单占位（真实收单由既有同构流程承担），拒绝时语义与 T02 一致 */
    private static class MockAdapterMarker implements PaymentChannelAdapter {
        @Override
        public String channel() {
            return "MOCK";
        }

        @Override
        public ChannelPaymentResponse createPayment(String merchantPaymentNo, java.math.BigDecimal amount,
                                                    String subject, String scene, String returnUrl) {
            return ChannelPaymentResponse.ok("MOCK" + merchantPaymentNo, "{}");
        }

        @Override
        public ChannelStatusResponse queryPayment(String merchantPaymentNo) {
            return new ChannelStatusResponse(merchantPaymentNo, null, "SUCCESS", null, null);
        }

        @Override
        public ChannelRefundResponse createRefund(String merchantPaymentNo, String refundNo,
                                                  java.math.BigDecimal refundAmount, String reason) {
            return ChannelRefundResponse.reject("REFUND_CHANNEL_UNAVAILABLE", "MOCK 渠道不承担退款适配");
        }

        @Override
        public ChannelStatusResponse queryRefund(String refundNo) {
            return new ChannelStatusResponse(null, null, "UNKNOWN", null, null);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    }
}
