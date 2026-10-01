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

    // T05：/payment-success/{orderId} 端点已删除（双路径状态推进消除），用例一并移除

    // T05：/cancel-notify/{orderId} 端点已删除（无生产调用方），用例一并移除
}
