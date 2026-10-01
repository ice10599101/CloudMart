package com.cloudmart.payment.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.payment.entity.PaymentAttempt;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T01：后台支付查询迁移到 payment_attempt 台账——
 * 列表分页信封、按订单查询、渠道事实（merchantPaymentNo/channel/providerTxnNo）可见。
 */
@DisplayName("AdminPaymentController 支付尝试查询")
class AdminPaymentControllerTest {

    private MockMvc mockMvc;

    private final PaymentAttemptMapper attemptMapper = Mockito.mock(PaymentAttemptMapper.class);

    @BeforeAll
    static void initEntityMeta() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PaymentAttempt.class);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminPaymentController(attemptMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PaymentAttempt attempt(Long id, Long orderId) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(id);
        attempt.setOrderId(orderId);
        attempt.setMerchantPaymentNo("MP20260101000001");
        attempt.setChannel("MOCK");
        attempt.setAmount(new BigDecimal("198.00"));
        attempt.setCurrency("CNY");
        attempt.setStatus("SUCCESS");
        attempt.setProviderTxnNo("MOCKTXN" + id);
        attempt.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return attempt;
    }

    @Test
    @DisplayName("管理端支付尝试列表 - 成功返回信封格式带分页与渠道事实")
    void listPayments_ShouldReturn200WithMeta() throws Exception {
        Page<PaymentAttempt> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(attempt(1L, 100L)));
        given(attemptMapper.selectPage(any(), any())).willReturn(page);

        mockMvc.perform(get("/admin/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].attemptId").value(1))
                .andExpect(jsonPath("$.data[0].merchantPaymentNo").value("MP20260101000001"))
                .andExpect(jsonPath("$.data[0].channel").value("MOCK"))
                .andExpect(jsonPath("$.data[0].providerTxnNo").value("MOCKTXN1"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("管理端按订单查询支付尝试 - 成功返回信封格式")
    void getPaymentByOrderId_ShouldReturn200WithEnvelope() throws Exception {
        given(attemptMapper.selectOne(any())).willReturn(attempt(1L, 100L));

        mockMvc.perform(get("/admin/payments/order/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(100))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("管理端按订单查询 - 无尝试返回 data:null（不虚构记录）")
    void getPaymentByOrderId_NoAttempt_ReturnsNullData() throws Exception {
        given(attemptMapper.selectOne(any())).willReturn(null);

        mockMvc.perform(get("/admin/payments/order/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
