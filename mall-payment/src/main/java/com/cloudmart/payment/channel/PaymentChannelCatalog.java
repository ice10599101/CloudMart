package com.cloudmart.payment.channel;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * R09 支付渠道目录：已知渠道的展示名与<b>场景→收单动作</b>能力矩阵（唯一权威，
 * 三端收银台不得自带渠道清单）。
 *
 * <p>目录只回答"这个渠道在该场景长什么样"；渠道当前是否真的可支付由
 * {@link ChannelRegistry} 的凭据白名单回答——真实渠道适配器落地时，必须在
 * 同一改动中注册本目录条目，二者缺一不可。</p>
 *
 * <p>能力矩阵（首条真实渠道接入时按渠道开放平台实际能力复核）：</p>
 * <ul>
 *   <li>ALIPAY：H5 跳转收银台（REDIRECT）；APP 支付宝 SDK 调起（SDK）；
 *       WeChat 小程序内无法调起支付宝，不提供 MINIAPP；</li>
 *   <li>WECHAT：H5 mweb 跳转（REDIRECT）；MINIAPP JSAPI（SDK）；APP SDK；</li>
 *   <li>BANK_CARD：H5/APP 网关页跳转（REDIRECT）；</li>
 *   <li>MOCK：全场景 NONE——无外部收单交互，测试环境由 mockCallback 闭环。</li>
 * </ul>
 */
@Component
public class PaymentChannelCatalog {

    public record ChannelDescriptor(String code, String displayName,
                                    Map<PaymentScene, PaymentAction> sceneActions) {

        public boolean supports(PaymentScene scene) {
            return sceneActions.containsKey(scene);
        }

        public PaymentAction action(PaymentScene scene) {
            return sceneActions.get(scene);
        }
    }

    private final Map<String, ChannelDescriptor> descriptors;

    public PaymentChannelCatalog() {
        this.descriptors = Map.of(
                "ALIPAY", new ChannelDescriptor("ALIPAY", "支付宝", Map.of(
                        PaymentScene.H5, PaymentAction.REDIRECT,
                        PaymentScene.APP, PaymentAction.SDK)),
                "WECHAT", new ChannelDescriptor("WECHAT", "微信支付", Map.of(
                        PaymentScene.H5, PaymentAction.REDIRECT,
                        PaymentScene.MINIAPP, PaymentAction.SDK,
                        PaymentScene.APP, PaymentAction.SDK)),
                "BANK_CARD", new ChannelDescriptor("BANK_CARD", "银行卡", Map.of(
                        PaymentScene.H5, PaymentAction.REDIRECT,
                        PaymentScene.APP, PaymentAction.REDIRECT)),
                "MOCK", new ChannelDescriptor("MOCK", "模拟支付", Map.of(
                        PaymentScene.H5, PaymentAction.NONE,
                        PaymentScene.MINIAPP, PaymentAction.NONE,
                        PaymentScene.APP, PaymentAction.NONE)));
    }

    /** 渠道编码归一化（大写）；未知渠道返回 empty。 */
    public Optional<ChannelDescriptor> find(String channel) {
        if (channel == null || channel.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(descriptors.get(channel.trim().toUpperCase()));
    }

    /** 已知渠道编码集（测试/注册表交叉校验用）。 */
    public Set<String> knownCodes() {
        return descriptors.keySet();
    }
}
