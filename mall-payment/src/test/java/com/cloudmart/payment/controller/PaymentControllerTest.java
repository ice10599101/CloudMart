package com.cloudmart.payment.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.payment.converter.PaymentConverter;
import com.cloudmart.payment.dto.CreatePaymentRequest;
import com.cloudmart.payment.dto.PaymentCallbackRequest;
import com.cloudmart.payment.dto.PaymentDTO;
import com.cloudmart.payment.service.PaymentService;
import com.cloudmart.payment.vo.PaymentVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerTest {

    private PaymentService paymentService;
    private PaymentConverter paymentConverter;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        paymentConverter = mock(PaymentConverter.class);
        PaymentController controller = new PaymentController(paymentService, paymentConverter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PaymentDTO buildPaymentDTO() {
        return new PaymentDTO(1L, 100L, "PAY001", new BigDecimal("100.00"), "MOCK", "PENDING", null, LocalDateTime.of(2026, 1, 1, 0, 0), "/payment/mock?paymentId=1");
    }

    private PaymentVO buildPaymentVO() {
        return new PaymentVO(1L, 100L, "PAY001", new BigDecimal("100.00"), "MOCK", "PENDING", null, LocalDateTime.of(2026, 1, 1, 0, 0), "/payment/mock?paymentId=1");
    }

    @Nested
    @DisplayName("POST /payments")
    class CreatePaymentTests {

        @Test
        @DisplayName("valid request -> creates payment and returns 200")
        void createPayment_ValidRequest_ShouldReturn200() throws Exception {
            PaymentDTO dto = buildPaymentDTO();
            when(paymentService.createPayment(any(CreatePaymentRequest.class))).thenReturn(dto);
            when(paymentConverter.dtoToVO(dto)).thenReturn(buildPaymentVO());

            CreatePaymentRequest request = new CreatePaymentRequest(100L, new BigDecimal("100.00"), "MOCK");

            mockMvc.perform(post("/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }
    }

    @Nested
    @DisplayName("POST /payments/callback")
    class HandleCallbackTests {

        @Test
        @DisplayName("SUCCESS callback -> returns 200")
        void handleCallback_Success_ShouldReturn200() throws Exception {
            PaymentDTO dto = buildPaymentDTO();
            when(paymentService.handleCallback(any(PaymentCallbackRequest.class))).thenReturn(dto);
            when(paymentConverter.dtoToVO(dto)).thenReturn(buildPaymentVO());

            PaymentCallbackRequest request = new PaymentCallbackRequest(1L, "SUCCESS", "TXN001");

            mockMvc.perform(post("/payments/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("POST /payments/{paymentId}/refund")
    class RefundTests {

        @Test
        @DisplayName("refund -> returns 200")
        void refund_ShouldReturn200() throws Exception {
            PaymentDTO dto = buildPaymentDTO();
            when(paymentService.refund(1L)).thenReturn(dto);
            when(paymentConverter.dtoToVO(dto)).thenReturn(buildPaymentVO());

            mockMvc.perform(post("/payments/1/refund"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /payments/order/{orderId}")
    class GetPaymentByOrderIdTests {

        @Test
        @DisplayName("payment exists -> returns 200")
        void getPaymentByOrderId_Exists_ShouldReturn200() throws Exception {
            PaymentDTO dto = buildPaymentDTO();
            when(paymentService.getPaymentByOrderId(100L)).thenReturn(dto);
            when(paymentConverter.dtoToVO(dto)).thenReturn(buildPaymentVO());

            mockMvc.perform(get("/payments/order/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderId").value(100));
        }
    }

    @Nested
    @DisplayName("PUT /payments/{paymentId}/simulate-success")
    class SimulatePaymentSuccessTests {

        @Test
        @DisplayName("simulate success -> returns 200")
        void simulatePaymentSuccess_ShouldReturn200() throws Exception {
            PaymentDTO dto = buildPaymentDTO();
            when(paymentService.simulatePaymentSuccess(1L)).thenReturn(dto);
            when(paymentConverter.dtoToVO(dto)).thenReturn(buildPaymentVO());

            mockMvc.perform(put("/payments/1/simulate-success"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
