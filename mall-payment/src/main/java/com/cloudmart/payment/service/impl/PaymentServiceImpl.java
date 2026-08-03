package com.cloudmart.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.converter.PaymentConverter;
import com.cloudmart.payment.dto.CreatePaymentRequest;
import com.cloudmart.payment.dto.PaymentCallbackRequest;
import com.cloudmart.payment.dto.PaymentDTO;
import com.cloudmart.payment.entity.Payment;
import com.cloudmart.payment.mq.PaymentEventProducer;
import com.cloudmart.payment.repository.PaymentMapper;
import com.cloudmart.payment.service.PaymentService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentConverter paymentConverter;
    private final PaymentEventProducer paymentEventProducer;

    @Override
    public Page<PaymentDTO> listPayments(String status, int page, int size) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<Payment>()
                .eq(status != null && !status.isEmpty(), Payment::getStatus, status)
                .orderByDesc(Payment::getCreatedAt);

        Page<Payment> paymentPage = paymentMapper.selectPage(new Page<>(page, size), wrapper);
        Page<PaymentDTO> dtoPage = new Page<>(paymentPage.getCurrent(), paymentPage.getSize(), paymentPage.getTotal());
        dtoPage.setRecords(paymentPage.getRecords().stream().map(paymentConverter::toDTO).toList());
        return dtoPage;
    }

    @Override
    @Transactional
    @SentinelResource(value = "createPayment", fallback = "createPaymentFallback")
    public PaymentDTO createPayment(CreatePaymentRequest request) {
        Payment existing = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, request.orderId())
                        .in(Payment::getStatus, "PENDING", "SUCCESS")
                        .orderByDesc(Payment::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return enrichPayUrl(paymentConverter.toDTO(existing));
        }

        Payment payment = new Payment();
        payment.setOrderId(request.orderId());
        payment.setPaymentNo(generatePaymentNo());
        payment.setAmount(request.amount());
        payment.setPayMethod(request.payMethod() != null ? request.payMethod() : "MOCK");
        payment.setStatus("PENDING");

        paymentMapper.insert(payment);
        return enrichPayUrl(paymentConverter.toDTO(payment));
    }

    @Override
    @Transactional
    @SentinelResource(value = "handleCallback", fallback = "handleCallbackFallback")
    public PaymentDTO handleCallback(PaymentCallbackRequest request) {
        Payment payment = paymentMapper.selectById(request.paymentId());
        if (payment == null) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "支付记录不存在");
        }

        if ("SUCCESS".equalsIgnoreCase(request.status())) {
            if (!"PENDING".equals(payment.getStatus())) {
                log.warn("支付回调状态跳过, paymentId={}, currentStatus={}", payment.getId(), payment.getStatus());
                return paymentConverter.toDTO(payment);
            }
            payment.setStatus("SUCCESS");
            payment.setPaidAt(LocalDateTime.now());
            paymentMapper.updateById(payment);

            try {
                paymentEventProducer.sendPaymentSuccess(payment.getOrderId(), payment.getId());
            } catch (Exception e) {
                log.error("通知订单支付成功失败, orderId={}: {}", payment.getOrderId(), e.getMessage());
            }
        } else if ("FAILED".equalsIgnoreCase(request.status())) {
            if ("PENDING".equals(payment.getStatus())) {
                payment.setStatus("FAILED");
                paymentMapper.updateById(payment);
            }
        }

        return paymentConverter.toDTO(payment);
    }

    @Override
    @Transactional
    public PaymentDTO refund(Long paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "支付记录不存在");
        }
        if ("REFUNDED".equals(payment.getStatus())) {
            return paymentConverter.toDTO(payment);
        }
        if (!"SUCCESS".equals(payment.getStatus())) {
            throw new BusinessException("PAYMENT_STATUS_ERROR", "只有支付成功的记录才能退款");
        }

        payment.setStatus("REFUNDED");
        paymentMapper.updateById(payment);

        try {
            paymentEventProducer.sendPaymentRefund(payment.getOrderId(), payment.getId());
        } catch (Exception e) {
            log.error("通知订单取消失败, orderId={}: {}", payment.getOrderId(), e.getMessage());
        }

        return paymentConverter.toDTO(payment);
    }

    @Override
    public PaymentDTO getPaymentByOrderId(Long orderId) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, orderId)
                        .orderByDesc(Payment::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (payment == null) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "支付记录不存在");
        }
        return enrichPayUrl(paymentConverter.toDTO(payment));
    }

    @Override
    @Transactional
    public PaymentDTO simulatePaymentSuccess(Long paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "支付记录不存在");
        }
        if (!"PENDING".equals(payment.getStatus())) {
            throw new BusinessException("PAYMENT_STATUS_ERROR", "只有待支付的记录才能模拟支付成功");
        }

        payment.setStatus("SUCCESS");
        payment.setPaidAt(LocalDateTime.now());
        paymentMapper.updateById(payment);

        try {
            paymentEventProducer.sendPaymentSuccess(payment.getOrderId(), payment.getId());
        } catch (Exception e) {
            log.error("模拟支付成功通知订单失败, orderId={}: {}", payment.getOrderId(), e.getMessage());
        }

        return paymentConverter.toDTO(payment);
    }

    private PaymentDTO enrichPayUrl(PaymentDTO dto) {
        if ("PENDING".equals(dto.status())) {
            String payUrl = "/payment/mock?paymentId=" + dto.id();
            return new PaymentDTO(
                    dto.id(), dto.orderId(), dto.paymentNo(), dto.amount(),
                    dto.payMethod(), dto.status(), dto.paidAt(), dto.createdAt(), payUrl
            );
        }
        return dto;
    }

    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    public PaymentDTO createPaymentFallback(CreatePaymentRequest request, Throwable throwable) {
        log.warn("createPayment fallback triggered: {}", throwable.getMessage());
        return null;
    }

    public PaymentDTO handleCallbackFallback(PaymentCallbackRequest request, Throwable throwable) {
        log.warn("handleCallback fallback triggered: {}", throwable.getMessage());
        return null;
    }
}
