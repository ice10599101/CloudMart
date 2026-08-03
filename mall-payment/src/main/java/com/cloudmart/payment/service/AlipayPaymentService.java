package com.cloudmart.payment.service;

import com.cloudmart.payment.dto.PaymentDTO;

/**
 * 支付宝支付服务接口。
 * 封装支付宝下单、回调验签、退款等操作。
 */
public interface AlipayPaymentService {

    /**
     * 创建支付宝支付订单，返回收银台 URL。
     */
    String createTrade(PaymentDTO payment);

    /**
     * 验证支付宝异步回调签名。
     */
    boolean verifyCallback(java.util.Map<String, String> params);

    /**
     * 发起支付宝退款。
     */
    boolean refund(Long paymentId, String refundReason);
}
