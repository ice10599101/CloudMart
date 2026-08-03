package com.cloudmart.payment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.payment.dto.CreatePaymentRequest;
import com.cloudmart.payment.dto.PaymentDTO;
import com.cloudmart.payment.dto.PaymentCallbackRequest;

public interface PaymentService {

    Page<PaymentDTO> listPayments(String status, int page, int size);

    PaymentDTO createPayment(CreatePaymentRequest request);

    PaymentDTO handleCallback(PaymentCallbackRequest request);

    PaymentDTO refund(Long paymentId);

    PaymentDTO getPaymentByOrderId(Long orderId);

    PaymentDTO simulatePaymentSuccess(Long paymentId);
}
