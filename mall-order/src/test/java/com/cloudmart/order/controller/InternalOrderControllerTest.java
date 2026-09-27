package com.cloudmart.order.controller;

import com.cloudmart.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-01 内部订单回调端点测试：payment-success / cancel-notify 迁移自
 * /orders/** 用户前缀路径，仅 mall-payment 的服务令牌（ROLE_INTERNAL）可达。
 */
class InternalOrderControllerTest {

    private MockMvc mockMvc;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = Mockito.mock(OrderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalOrderController(orderService))
                .build();
    }

    @Test
    @DisplayName("POST /internal/orders/payment-success/{orderId} -> 200 且调用服务")
    void notifyPaymentSuccess_ShouldReturn200() throws Exception {
        mockMvc.perform(post("/internal/orders/payment-success/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(orderService).notifyPaymentSuccess(1L);
    }

    @Test
    @DisplayName("POST /internal/orders/cancel-notify/{orderId} -> 200 且调用服务")
    void notifyOrderCancel_ShouldReturn200() throws Exception {
        mockMvc.perform(post("/internal/orders/cancel-notify/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(orderService).notifyOrderCancel(1L);
    }
}
