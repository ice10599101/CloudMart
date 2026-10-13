package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.OrderFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOrderControllerTest {

    private MockMvc mockMvc;
    private OrderFeignClient orderFeignClient;

    @BeforeEach
    void setUp() {
        orderFeignClient = mock(OrderFeignClient.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminOrderController(orderFeignClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listOrders_returnsOrderList() throws Exception {
        given(orderFeignClient.listOrders(null, null, null, 1, 10))
                .willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/orders").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getOrder_returnsOrderDetail() throws Exception {
        given(orderFeignClient.getOrderById(1L)).willReturn(ApiResponse.ok("order"));

        mockMvc.perform(get("/business/orders/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shipOrder_orderShippedSuccessfully() throws Exception {
        given(orderFeignClient.shipOrder(eq(1L), any()))
                .willReturn(ApiResponse.ok("order"));

        mockMvc.perform(put("/business/orders/1/ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"SF\",\"trackingNo\":\"SF123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void cancelOrder_orderCancelledSuccessfully() throws Exception {
        given(orderFeignClient.cancelOrder(1L)).willReturn(ApiResponse.ok("order"));

        mockMvc.perform(put("/business/orders/1/cancel").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void approveRefund_refundApprovedSuccessfully() throws Exception {
        given(orderFeignClient.approveRefund(1L)).willReturn(ApiResponse.ok("order"));

        mockMvc.perform(put("/business/orders/1/refund/approve").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void rejectRefund_refundRejectedSuccessfully() throws Exception {
        given(orderFeignClient.rejectRefund(1L, "不符合退款条件"))
                .willReturn(ApiResponse.ok("order"));

        mockMvc.perform(put("/business/orders/1/refund/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"不符合退款条件\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
