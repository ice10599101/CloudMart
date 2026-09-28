package com.cloudmart.payment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.payment.dto.CreatePaymentRequest;
import com.cloudmart.payment.dto.PaymentDTO;
import com.cloudmart.payment.dto.PaymentCallbackRequest;

public interface PaymentService {

    Page<PaymentDTO> listPayments(String status, int page, int size);

    /**
     * 创建支付（SEC-04 对象归属）。
     *
     * @param callerUserId 发起用户；null 表示可信内部调用方（如 mall-order 服务令牌），
     *                     其归属由调用方服务保证；用户调用必须与订单归属一致
     */
    PaymentDTO createPayment(CreatePaymentRequest request, Long callerUserId);

    PaymentDTO handleCallback(PaymentCallbackRequest request);

    PaymentDTO refund(Long paymentId);

    /** 查询订单支付状态（SEC-04：用户调用校验订单归属，内部调用方传 null） */
    PaymentDTO getPaymentByOrderId(Long orderId, Long callerUserId);

    PaymentDTO simulatePaymentSuccess(Long paymentId);
}
