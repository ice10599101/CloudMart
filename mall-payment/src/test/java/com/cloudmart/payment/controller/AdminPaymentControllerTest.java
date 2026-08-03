package com.cloudmart.payment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.payment.converter.PaymentConverter;
import com.cloudmart.payment.dto.PaymentDTO;
import com.cloudmart.payment.service.PaymentService;
import com.cloudmart.payment.vo.PaymentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPaymentControllerTest {

    private MockMvc mockMvc;

    private final PaymentService paymentService = Mockito.mock(PaymentService.class);
    private final PaymentConverter paymentConverter = Mockito.mock(PaymentConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminPaymentController(paymentService, paymentConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("管理端支付列表 - 成功返回信封格式带分页")
    void listPayments_ShouldReturn200WithMeta() throws Exception {
        PaymentDTO dto = new PaymentDTO(1L, 100L, "PAY20260101001", new BigDecimal("198.00"),
                "ALIPAY", "PAID", LocalDateTime.now(), LocalDateTime.now(), null);
        Page<PaymentDTO> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(dto));

        given(paymentService.listPayments(null, 1, 20)).willReturn(page);

        PaymentVO vo = new PaymentVO(1L, 100L, "PAY20260101001", new BigDecimal("198.00"),
                "ALIPAY", "PAID", LocalDateTime.now(), LocalDateTime.now(), null);
        given(paymentConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("管理端查询支付记录 - 成功返回信封格式")
    void getPaymentByOrderId_ShouldReturn200WithEnvelope() throws Exception {
        PaymentDTO dto = new PaymentDTO(1L, 100L, "PAY20260101001", new BigDecimal("198.00"),
                "ALIPAY", "PAID", LocalDateTime.now(), LocalDateTime.now(), null);

        given(paymentService.getPaymentByOrderId(100L)).willReturn(dto);

        PaymentVO vo = new PaymentVO(1L, 100L, "PAY20260101001", new BigDecimal("198.00"),
                "ALIPAY", "PAID", LocalDateTime.now(), LocalDateTime.now(), null);
        given(paymentConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/payments/order/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(100));
    }

    @Test
    @DisplayName("管理端退款 - 成功返回信封格式")
    void refund_ShouldReturn200WithEnvelope() throws Exception {
        PaymentDTO dto = new PaymentDTO(1L, 100L, "PAY20260101001", new BigDecimal("198.00"),
                "ALIPAY", "REFUNDED", LocalDateTime.now(), LocalDateTime.now(), null);

        given(paymentService.refund(1L)).willReturn(dto);

        PaymentVO vo = new PaymentVO(1L, 100L, "PAY20260101001", new BigDecimal("198.00"),
                "ALIPAY", "REFUNDED", LocalDateTime.now(), LocalDateTime.now(), null);
        given(paymentConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/admin/payments/1/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("管理端查询不存在的支付记录 - 返回错误信封")
    void getPaymentByOrderId_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(paymentService.getPaymentByOrderId(999L))
                .willThrow(new BusinessException("PAYMENT_NOT_FOUND", "支付记录不存在"));

        mockMvc.perform(get("/admin/payments/order/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("支付记录不存在"));
    }
}
