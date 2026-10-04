package com.cloudmart.payment.channel;

import java.math.BigDecimal;

/**
 * 支付/退款渠道适配接口（T07）：真实交易开放前必须经本接口接入渠道——
 * MOCK 渠道仅限 dev/test profile；未知渠道在创建支付尝试<b>前</b>被拒绝，
 * 不产生"看似可支付"的记录。
 *
 * <p>实现约定（渠道接入清单）：</p>
 * <ul>
 *   <li>创建支付 → 渠道收单参数（H5/小程序/原生按端下发）；</li>
 *   <li>查询支付 → 按原商户单号收敛 UNKNOWN/PROCESSING，不直接重新发款；</li>
 *   <li>发起退款/查询退款 → 退款单状态机（REQUESTED→PROCESSING→SUCCEEDED/FAILED）；</li>
 *   <li>验签回调 → 渠道事实入口，重复/乱序由 Inbox 幂等吸收；</li>
 *   <li>所有外部调用必须有超时——超时进入 UNKNOWN 等待查单收敛，绝不盲目重试。</li>
 * </ul>
 *
 * <p>MOCK 渠道由既有同构流程覆盖（REQUESTED→PROCESSING→SUCCEEDED 同事务 Outbox），
 * 不经本接口——生产 profile 禁止 MOCK。</p>
 */
public interface PaymentChannelAdapter {

    /** @return 渠道标识（与 payment_attempt.channel 一致，如 ALIPAY/WECHAT） */
    String channel();

    /**
     * 创建支付（收单）。
     *
     * @param merchantPaymentNo 商户支付单号（幂等键）
     * @param amount            应付金额（十进制，币种 CNY）
     * @param subject           商品描述
     * @param scene             支付场景：H5/MINIAPP/APP
     * @param returnUrl         支付完成回跳地址（H5）
     * @return 渠道收单参数（按 scene 结构不同，由端侧解释）
     */
    ChannelPaymentResponse createPayment(String merchantPaymentNo, BigDecimal amount,
                                         String subject, String scene, String returnUrl);

    /** 查询支付：按原商户单号收敛，不重新发款；返回渠道侧权威状态 */
    ChannelStatusResponse queryPayment(String merchantPaymentNo);

    /** 发起退款：渠道退款单号幂等；超时进入 UNKNOWN 由查单收敛 */
    ChannelRefundResponse createRefund(String merchantPaymentNo, String refundNo,
                                       BigDecimal refundAmount, String reason);

    /** 查询退款：渠道侧权威状态 */
    ChannelStatusResponse queryRefund(String refundNo);

    /** 渠道可用性（凭据是否配置齐全；未配置的渠道不进白名单） */
    boolean isConfigured();

    /** 渠道收单参数（createPayment 结果） */
    record ChannelPaymentResponse(boolean accepted, String channelTradeNo, String payParams,
                                  String errorCode, String errorMessage) {
        public static ChannelPaymentResponse ok(String channelTradeNo, String payParams) {
            return new ChannelPaymentResponse(true, channelTradeNo, payParams, null, null);
        }

        public static ChannelPaymentResponse fail(String code, String message) {
            return new ChannelPaymentResponse(false, null, null, code, message);
        }
    }

    /** 渠道状态（支付/退款通用）：payStatus/refundStatus ∈ SUCCESS/PROCESSING/FAILED/UNKNOWN */
    record ChannelStatusResponse(String merchantNo, String channelTradeNo,
                                 String status, String errorCode, String errorMessage) {
    }

    /** 渠道退款受理结果：accepted=false 表示明确拒绝（不进 UNKNOWN） */
    record ChannelRefundResponse(boolean accepted, String channelRefundNo,
                                 String errorCode, String errorMessage) {
        public static ChannelRefundResponse ok(String channelRefundNo) {
            return new ChannelRefundResponse(true, channelRefundNo, null, null);
        }

        public static ChannelRefundResponse reject(String code, String message) {
            return new ChannelRefundResponse(false, null, code, message);
        }
    }
}
