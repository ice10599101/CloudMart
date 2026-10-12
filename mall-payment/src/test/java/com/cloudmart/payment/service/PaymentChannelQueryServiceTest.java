package com.cloudmart.payment.service;

import com.cloudmart.payment.channel.ChannelRegistry;
import com.cloudmart.payment.channel.PaymentAction;
import com.cloudmart.payment.channel.PaymentChannelAdapter;
import com.cloudmart.payment.channel.PaymentChannelCatalog;
import com.cloudmart.payment.channel.PaymentScene;
import com.cloudmart.payment.dto.ChannelView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * R09 渠道可用性查询：白名单 ∩ 目录场景能力；维护中标记；无凭据环境返回空列表
 * （收银台"暂不可支付"，禁止假按钮）。
 */
@DisplayName("PaymentChannelQueryService 渠道可用性")
class PaymentChannelQueryServiceTest {

    private static PaymentChannelAdapter configuredAdapter(String channel) {
        return new PaymentChannelAdapter() {
            @Override
            public String channel() {
                return channel;
            }

            @Override
            public ChannelPaymentResponse createPayment(String merchantPaymentNo, BigDecimal amount,
                                                        String subject, String scene, String returnUrl) {
                return ChannelPaymentResponse.ok("TX", "{}");
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

    /** 未配置凭据（isConfigured=false）的适配器不进白名单 */
    private static PaymentChannelAdapter unconfiguredAdapter(String channel) {
        return new PaymentChannelAdapter() {
            @Override
            public String channel() {
                return channel;
            }

            @Override
            public ChannelPaymentResponse createPayment(String merchantPaymentNo, BigDecimal amount,
                                                        String subject, String scene, String returnUrl) {
                throw new IllegalStateException("未配置渠道不应被调用");
            }

            @Override
            public ChannelStatusResponse queryPayment(String merchantPaymentNo) {
                throw new IllegalStateException("未配置渠道不应被调用");
            }

            @Override
            public ChannelRefundResponse createRefund(String merchantPaymentNo, String refundNo,
                                                      BigDecimal refundAmount, String reason) {
                throw new IllegalStateException("未配置渠道不应被调用");
            }

            @Override
            public ChannelStatusResponse queryRefund(String refundNo) {
                throw new IllegalStateException("未配置渠道不应被调用");
            }

            @Override
            public boolean isConfigured() {
                return false;
            }
        };
    }

    @Test
    @DisplayName("MOCK 开启（dev/test 默认）：三场景只返回 MOCK/NONE，无真实渠道假按钮")
    void listChannels_mockOnly() {
        ChannelRegistry registry = new ChannelRegistry(List.of(), true, mock(Environment.class));
        PaymentChannelQueryService service = new PaymentChannelQueryService(
                registry, new PaymentChannelCatalog(), "");

        List<ChannelView> h5 = service.listChannels(PaymentScene.H5);
        assertThat(h5).hasSize(1);
        assertThat(h5.get(0).channel()).isEqualTo("MOCK");
        assertThat(h5.get(0).action()).isEqualTo("NONE");
        assertThat(h5.get(0).maintenance()).isFalse();

        assertThat(service.listChannels(PaymentScene.MINIAPP)).extracting(ChannelView::channel)
                .containsExactly("MOCK");
        assertThat(service.listChannels(PaymentScene.APP)).extracting(ChannelView::channel)
                .containsExactly("MOCK");
    }

    @Test
    @DisplayName("生产形态：MOCK 关闭且无真实凭据适配器 → 空列表（端侧展示暂不可支付）")
    void listChannels_noConfiguredAdapters_empty() {
        ChannelRegistry registry = new ChannelRegistry(
                List.of(unconfiguredAdapter("WECHAT")), false, mock(Environment.class));
        PaymentChannelQueryService service = new PaymentChannelQueryService(
                registry, new PaymentChannelCatalog(), "");

        assertThat(service.listChannels(PaymentScene.H5)).isEmpty();
        assertThat(registry.availableChannelCodes()).isEmpty();
    }

    @Test
    @DisplayName("场景过滤：ALIPAY 仅 H5/APP；MINIAPP 不出现；WECHAT 三场景动作各异")
    void listChannels_sceneFilterAndActions() {
        ChannelRegistry registry = new ChannelRegistry(
                List.of(configuredAdapter("ALIPAY"), configuredAdapter("WECHAT")),
                false, mock(Environment.class));
        PaymentChannelQueryService service = new PaymentChannelQueryService(
                registry, new PaymentChannelCatalog(), "");

        assertThat(service.listChannels(PaymentScene.H5)).extracting(ChannelView::channel)
                .containsExactly("ALIPAY", "WECHAT");
        assertThat(service.listChannels(PaymentScene.MINIAPP)).extracting(ChannelView::channel)
                .containsExactly("WECHAT");
        assertThat(service.listChannels(PaymentScene.APP)).extracting(ChannelView::channel)
                .containsExactly("ALIPAY", "WECHAT");

        ChannelView alipayH5 = service.listChannels(PaymentScene.H5).stream()
                .filter(v -> v.channel().equals("ALIPAY")).findFirst().orElseThrow();
        assertThat(alipayH5.action()).isEqualTo(PaymentAction.REDIRECT.name());
        ChannelView wechatMini = service.listChannels(PaymentScene.MINIAPP).get(0);
        assertThat(wechatMini.action()).isEqualTo(PaymentAction.SDK.name());
    }

    @Test
    @DisplayName("维护中渠道保留返回但置 maintenance=true（端侧置灰不可选）")
    void listChannels_maintenanceFlagged() {
        ChannelRegistry registry = new ChannelRegistry(
                List.of(configuredAdapter("ALIPAY"), configuredAdapter("WECHAT")),
                false, mock(Environment.class));
        PaymentChannelQueryService service = new PaymentChannelQueryService(
                registry, new PaymentChannelCatalog(), "wechat, alipay");

        List<ChannelView> views = service.listChannels(PaymentScene.H5);
        assertThat(views).allMatch(ChannelView::maintenance);
    }

    @Test
    @DisplayName("actionOf：未知渠道/不支持场景返回 empty")
    void actionOf_unknownOrUnsupported() {
        PaymentChannelQueryService service = new PaymentChannelQueryService(
                new ChannelRegistry(List.of(), true, mock(Environment.class)),
                new PaymentChannelCatalog(), "");

        assertThat(service.actionOf("UNKNOWN", PaymentScene.H5)).isEmpty();
        assertThat(service.actionOf("ALIPAY", PaymentScene.MINIAPP)).isEmpty();
        assertThat(service.actionOf("WECHAT", PaymentScene.APP)).contains(PaymentAction.SDK);
    }
}
