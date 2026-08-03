package com.cloudmart.payment.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.converter.PaymentConverter;
import com.cloudmart.payment.dto.CreatePaymentRequest;
import com.cloudmart.payment.dto.PaymentCallbackRequest;
import com.cloudmart.payment.dto.PaymentDTO;
import com.cloudmart.payment.entity.Payment;
import com.cloudmart.payment.mq.PaymentEventProducer;
import com.cloudmart.payment.repository.PaymentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class PaymentServiceImplTest {

    private PaymentMapper paymentMapper;
    private PaymentConverter paymentConverter;
    private PaymentEventProducer paymentEventProducer;
    private PaymentServiceImpl paymentService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(Payment.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.payment.repository.PaymentMapper");
            TableInfoHelper.initTableInfo(assistant, Payment.class);
        }
    }

    @BeforeEach
    void setUp() {
        paymentMapper = mock(PaymentMapper.class);
        paymentConverter = mock(PaymentConverter.class);
        paymentEventProducer = mock(PaymentEventProducer.class);
        paymentService = new PaymentServiceImpl(paymentMapper, paymentConverter, paymentEventProducer);
    }

    private Payment buildPayment(Long id, Long orderId, String status) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setOrderId(orderId);
        payment.setPaymentNo("PAY20260101000001");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setPayMethod("MOCK");
        payment.setStatus(status);
        payment.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return payment;
    }

    private PaymentDTO buildPaymentDTO(Payment payment) {
        return new PaymentDTO(
                payment.getId(), payment.getOrderId(), payment.getPaymentNo(),
                payment.getAmount(), payment.getPayMethod(), payment.getStatus(),
                payment.getPaidAt(), payment.getCreatedAt(), null
        );
    }

    @Nested
    @DisplayName("createPayment")
    class CreatePaymentTests {

        @Test
        @DisplayName("no existing payment -> creates new payment")
        void createPayment_NoExisting_ShouldCreate() {
            when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            PaymentDTO dto = new PaymentDTO(1L, 100L, "PAY20260101000001", new BigDecimal("100.00"), "MOCK", "PENDING", null, LocalDateTime.of(2026, 1, 1, 0, 0), null);
            when(paymentConverter.toDTO(any(Payment.class))).thenReturn(dto);

            CreatePaymentRequest request = new CreatePaymentRequest(100L, new BigDecimal("100.00"), "MOCK");
            PaymentDTO result = paymentService.createPayment(request);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("PENDING");
            verify(paymentMapper).insert(any(Payment.class));
        }

        @Test
        @DisplayName("existing PENDING payment -> returns existing")
        void createPayment_ExistingPending_ShouldReturnExisting() {
            Payment existing = buildPayment(1L, 100L, "PENDING");
            when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            PaymentDTO dto = buildPaymentDTO(existing);
            when(paymentConverter.toDTO(existing)).thenReturn(dto);

            CreatePaymentRequest request = new CreatePaymentRequest(100L, new BigDecimal("100.00"), "MOCK");
            PaymentDTO result = paymentService.createPayment(request);

            assertThat(result).isNotNull();
            verify(paymentMapper, never()).insert(any(Payment.class));
        }

        @Test
        @DisplayName("null payMethod -> defaults to MOCK")
        void createPayment_NullPayMethod_ShouldDefaultToMock() {
            when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            PaymentDTO dto = new PaymentDTO(1L, 100L, "PAY001", new BigDecimal("100.00"), "MOCK", "PENDING", null, null, null);
            when(paymentConverter.toDTO(any(Payment.class))).thenReturn(dto);

            CreatePaymentRequest request = new CreatePaymentRequest(100L, new BigDecimal("100.00"), null);
            PaymentDTO result = paymentService.createPayment(request);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleCallback")
    class HandleCallbackTests {

        @Test
        @DisplayName("SUCCESS callback on PENDING payment -> updates to SUCCESS")
        void handleCallback_SuccessOnPending_ShouldUpdateToSuccess() {
            Payment payment = buildPayment(1L, 100L, "PENDING");
            when(paymentMapper.selectById(1L)).thenReturn(payment);

            PaymentDTO dto = buildPaymentDTO(payment);
            when(paymentConverter.toDTO(payment)).thenReturn(dto);

            PaymentCallbackRequest request = new PaymentCallbackRequest(1L, "SUCCESS", "TXN001");
            PaymentDTO result = paymentService.handleCallback(request);

            assertThat(payment.getStatus()).isEqualTo("SUCCESS");
            assertThat(payment.getPaidAt()).isNotNull();
            verify(paymentMapper).updateById(payment);
            verify(paymentEventProducer).sendPaymentSuccess(100L, 1L);
        }

        @Test
        @DisplayName("FAILED callback on PENDING payment -> updates to FAILED")
        void handleCallback_FailedOnPending_ShouldUpdateToFailed() {
            Payment payment = buildPayment(1L, 100L, "PENDING");
            when(paymentMapper.selectById(1L)).thenReturn(payment);

            PaymentDTO dto = buildPaymentDTO(payment);
            when(paymentConverter.toDTO(payment)).thenReturn(dto);

            PaymentCallbackRequest request = new PaymentCallbackRequest(1L, "FAILED", null);
            PaymentDTO result = paymentService.handleCallback(request);

            assertThat(payment.getStatus()).isEqualTo("FAILED");
            verify(paymentMapper).updateById(payment);
            verify(paymentEventProducer, never()).sendPaymentSuccess(anyLong(), anyLong());
        }

        @Test
        @DisplayName("SUCCESS callback on already SUCCESS payment -> skips update")
        void handleCallback_SuccessOnAlreadySuccess_ShouldSkip() {
            Payment payment = buildPayment(1L, 100L, "SUCCESS");
            payment.setPaidAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            when(paymentMapper.selectById(1L)).thenReturn(payment);

            PaymentDTO dto = buildPaymentDTO(payment);
            when(paymentConverter.toDTO(payment)).thenReturn(dto);

            PaymentCallbackRequest request = new PaymentCallbackRequest(1L, "SUCCESS", "TXN001");
            PaymentDTO result = paymentService.handleCallback(request);

            verify(paymentMapper, never()).updateById(any(Payment.class));
        }

        @Test
        @DisplayName("payment not found -> throws PAYMENT_NOT_FOUND")
        void handleCallback_NotFound_ShouldThrowBusinessException() {
            when(paymentMapper.selectById(999L)).thenReturn(null);

            PaymentCallbackRequest request = new PaymentCallbackRequest(999L, "SUCCESS", null);

            assertThatThrownBy(() -> paymentService.handleCallback(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PAYMENT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("refund")
    class RefundTests {

        @Test
        @DisplayName("SUCCESS payment -> refunds successfully")
        void refund_SuccessPayment_ShouldRefund() {
            Payment payment = buildPayment(1L, 100L, "SUCCESS");
            when(paymentMapper.selectById(1L)).thenReturn(payment);

            PaymentDTO dto = buildPaymentDTO(payment);
            when(paymentConverter.toDTO(payment)).thenReturn(dto);

            PaymentDTO result = paymentService.refund(1L);

            assertThat(payment.getStatus()).isEqualTo("REFUNDED");
            verify(paymentMapper).updateById(payment);
            verify(paymentEventProducer).sendPaymentRefund(100L, 1L);
        }

        @Test
        @DisplayName("already REFUNDED payment -> returns without changes")
        void refund_AlreadyRefunded_ShouldReturnWithoutChanges() {
            Payment payment = buildPayment(1L, 100L, "REFUNDED");
            when(paymentMapper.selectById(1L)).thenReturn(payment);

            PaymentDTO dto = buildPaymentDTO(payment);
            when(paymentConverter.toDTO(payment)).thenReturn(dto);

            PaymentDTO result = paymentService.refund(1L);

            verify(paymentMapper, never()).updateById(any(Payment.class));
        }

        @Test
        @DisplayName("PENDING payment -> throws PAYMENT_STATUS_ERROR")
        void refund_PendingPayment_ShouldThrowBusinessException() {
            Payment payment = buildPayment(1L, 100L, "PENDING");
            when(paymentMapper.selectById(1L)).thenReturn(payment);

            assertThatThrownBy(() -> paymentService.refund(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PAYMENT_STATUS_ERROR"));
        }

        @Test
        @DisplayName("payment not found -> throws PAYMENT_NOT_FOUND")
        void refund_NotFound_ShouldThrowBusinessException() {
            when(paymentMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> paymentService.refund(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PAYMENT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("simulatePaymentSuccess")
    class SimulatePaymentSuccessTests {

        @Test
        @DisplayName("PENDING payment -> simulates success")
        void simulatePaymentSuccess_PendingPayment_ShouldSucceed() {
            Payment payment = buildPayment(1L, 100L, "PENDING");
            when(paymentMapper.selectById(1L)).thenReturn(payment);

            PaymentDTO dto = buildPaymentDTO(payment);
            when(paymentConverter.toDTO(payment)).thenReturn(dto);

            PaymentDTO result = paymentService.simulatePaymentSuccess(1L);

            assertThat(payment.getStatus()).isEqualTo("SUCCESS");
            assertThat(payment.getPaidAt()).isNotNull();
            verify(paymentMapper).updateById(payment);
            verify(paymentEventProducer).sendPaymentSuccess(100L, 1L);
        }

        @Test
        @DisplayName("non-PENDING payment -> throws PAYMENT_STATUS_ERROR")
        void simulatePaymentSuccess_NonPending_ShouldThrowBusinessException() {
            Payment payment = buildPayment(1L, 100L, "SUCCESS");
            when(paymentMapper.selectById(1L)).thenReturn(payment);

            assertThatThrownBy(() -> paymentService.simulatePaymentSuccess(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PAYMENT_STATUS_ERROR"));
        }

        @Test
        @DisplayName("payment not found -> throws PAYMENT_NOT_FOUND")
        void simulatePaymentSuccess_NotFound_ShouldThrowBusinessException() {
            when(paymentMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> paymentService.simulatePaymentSuccess(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PAYMENT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("getPaymentByOrderId")
    class GetPaymentByOrderIdTests {

        @Test
        @DisplayName("payment exists -> returns PaymentDTO")
        void getPaymentByOrderId_Exists_ShouldReturnDTO() {
            Payment payment = buildPayment(1L, 100L, "SUCCESS");
            when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(payment);

            PaymentDTO dto = buildPaymentDTO(payment);
            when(paymentConverter.toDTO(payment)).thenReturn(dto);

            PaymentDTO result = paymentService.getPaymentByOrderId(100L);

            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("payment not found -> throws PAYMENT_NOT_FOUND")
        void getPaymentByOrderId_NotFound_ShouldThrowBusinessException() {
            when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> paymentService.getPaymentByOrderId(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PAYMENT_NOT_FOUND"));
        }
    }
}
