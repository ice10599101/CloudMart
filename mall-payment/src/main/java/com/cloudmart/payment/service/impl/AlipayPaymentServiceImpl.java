package com.cloudmart.payment.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.cloudmart.payment.config.AlipayConfig;
import com.cloudmart.payment.dto.PaymentDTO;
import com.cloudmart.payment.service.AlipayPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 支付宝支付服务实现。
 * 仅在 AlipayConfig 激活时创建（即 alipay.enabled=true）。
 */
@Service
@ConditionalOnBean(AlipayConfig.class)
public class AlipayPaymentServiceImpl implements AlipayPaymentService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPaymentServiceImpl.class);

    private final AlipayClient alipayClient;
    private final AlipayConfig alipayConfig;

    public AlipayPaymentServiceImpl(AlipayClient alipayClient, AlipayConfig alipayConfig) {
        this.alipayClient = alipayClient;
        this.alipayConfig = alipayConfig;
    }

    @Override
    public String createTrade(PaymentDTO payment) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        request.setReturnUrl(alipayConfig.getReturnUrl());

        String bizContent = String.format(
                "{\"out_trade_no\":\"%s\",\"total_amount\":\"%s\",\"subject\":\"CloudMart订单-%s\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}",
                payment.paymentNo(), payment.amount(), payment.orderId()
        );
        request.setBizContent(bizContent);

        try {
            return alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            log.error("Alipay trade creation failed: {}", e.getErrMsg());
            throw new RuntimeException("支付宝下单失败: " + e.getErrMsg());
        }
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(params, alipayConfig.getAlipayPublicKey(), "UTF-8", "RSA2");
        } catch (AlipayApiException e) {
            log.error("Alipay callback verification failed: {}", e.getErrMsg());
            return false;
        }
    }

    @Override
    public boolean refund(Long paymentId, String refundReason) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        String bizContent = String.format(
                "{\"out_trade_no\":\"%s\",\"refund_amount\":\"%s\",\"refund_reason\":\"%s\"}",
                paymentId, refundReason, refundReason
        );
        request.setBizContent(bizContent);

        try {
            var response = alipayClient.execute(request);
            if (response.isSuccess()) {
                log.info("Alipay refund success: paymentId={}", paymentId);
                return true;
            }
            log.error("Alipay refund failed: {}", response.getSubMsg());
            return false;
        } catch (AlipayApiException e) {
            log.error("Alipay refund API error: {}", e.getErrMsg());
            return false;
        }
    }
}
