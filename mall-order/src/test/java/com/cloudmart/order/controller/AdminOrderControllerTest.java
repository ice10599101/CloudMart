package com.cloudmart.order.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.order.converter.OrderConverter;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.dto.OrderItemDTO;
import com.cloudmart.order.dto.OrderTodayStatsResponse;
import com.cloudmart.order.service.OrderService;
import com.cloudmart.order.vo.OrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOrderControllerTest {

    private MockMvc mockMvc;

    private final OrderService orderService = Mockito.mock(OrderService.class);
    private final OrderConverter orderConverter = Mockito.mock(OrderConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminOrderController(orderService, orderConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("分页查询所有订单 - 成功返回信封格式")
    void listAllOrders_ShouldReturn200WithEnvelope() throws Exception {
        OrderItemDTO itemDto = new OrderItemDTO(10L, 300L, 200L, "商品A", "img.jpg", "红色", new BigDecimal("99.00"), 2);
        OrderDTO dto = new OrderDTO(1L, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, null, "PAID", "张三", "13800138000", "地址",
                null, null, null, null, List.of(itemDto), null, null);
        Meta meta = new Meta(1, 10, 1L);
        ApiResponse<List<OrderDTO>> response = ApiResponse.ok(List.of(dto), meta);

        given(orderService.listAllOrders(null, null, null, 1, 10)).willReturn(response);

        OrderVO vo = new OrderVO(1L, "ORD123", "PAID", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, null);
        given(orderConverter.orderDtoToVOList(List.of(dto))).willReturn(List.of(vo));

        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(10))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("查询订单详情 - 成功返回信封格式")
    void getOrderById_ShouldReturn200WithEnvelope() throws Exception {
        OrderDTO dto = new OrderDTO(1L, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, null, "PAID", "张三", "13800138000", "地址",
                null, null, null, null, List.of(), null, null);

        given(orderService.getAdminOrderById(1L)).willReturn(dto);

        OrderVO vo = new OrderVO(1L, "ORD123", "PAID", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), null, null);
        given(orderConverter.orderDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.orderNo").value("ORD123"));
    }

    @Test
    @DisplayName("订单发货 - 成功返回信封格式")
    void shipOrder_ShouldReturn200WithEnvelope() throws Exception {
        OrderDTO dto = new OrderDTO(1L, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, null, "SHIPPED", "张三", "13800138000", "地址",
                null, null, null, null, List.of(), null, null);

        given(orderService.shipOrder(1L)).willReturn(dto);

        OrderVO vo = new OrderVO(1L, "ORD123", "SHIPPED", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), null, null);
        given(orderConverter.orderDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/orders/1/ship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("取消订单 - 成功返回信封格式")
    void cancelOrder_ShouldReturn200WithEnvelope() throws Exception {
        OrderDTO dto = new OrderDTO(1L, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, null, "CANCELLED", "张三", "13800138000", "地址",
                null, null, null, null, List.of(), null, null);

        given(orderService.adminCancelOrder(1L)).willReturn(dto);

        OrderVO vo = new OrderVO(1L, "ORD123", "CANCELLED", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), null, null);
        given(orderConverter.orderDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("同意退款 - 成功返回信封格式")
    void approveRefund_ShouldReturn200WithEnvelope() throws Exception {
        OrderDTO dto = new OrderDTO(1L, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, null, "REFUNDED", "张三", "13800138000", "地址",
                null, null, null, null, List.of(), null, null);

        given(orderService.approveRefund(1L)).willReturn(dto);

        OrderVO vo = new OrderVO(1L, "ORD123", "REFUNDED", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), null, null);
        given(orderConverter.orderDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/orders/1/approve-refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("拒绝退款 - 成功返回信封格式")
    void rejectRefund_ShouldReturn200WithEnvelope() throws Exception {
        OrderDTO dto = new OrderDTO(1L, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, null, "REFUND_REJECTED", "张三", "13800138000", "地址",
                null, null, "商品已拆封", null, List.of(), null, null);

        given(orderService.rejectRefund(1L, "商品已拆封")).willReturn(dto);

        OrderVO vo = new OrderVO(1L, "ORD123", "REFUND_REJECTED", new BigDecimal("198.00"), new BigDecimal("198.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), null, null);
        given(orderConverter.orderDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/orders/1/reject-refund")
                        .param("rejectReason", "商品已拆封"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REFUND_REJECTED"));
    }

    @Test
    @DisplayName("今日订单统计 - 成功返回信封格式")
    void getTodayStats_ShouldReturn200WithEnvelope() throws Exception {
        OrderTodayStatsResponse stats = new OrderTodayStatsResponse(15L, new BigDecimal("2980.00"));
        given(orderService.getTodayStats()).willReturn(ApiResponse.ok(stats));

        mockMvc.perform(get("/admin/orders/today-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.todayOrderCount").value(15))
                .andExpect(jsonPath("$.data.todayRevenue").value(2980.00));
    }

    @Test
    @DisplayName("查询不存在的订单 - 返回错误信封")
    void getOrderById_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(orderService.getAdminOrderById(999L))
                .willThrow(new BusinessException("ORDER_NOT_FOUND", "订单不存在"));

        mockMvc.perform(get("/admin/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("订单不存在"));
    }

    @Test
    @DisplayName("发货不存在的订单 - 返回错误信封")
    void shipOrder_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(orderService.shipOrder(999L))
                .willThrow(new BusinessException("ORDER_NOT_FOUND", "订单不存在"));

        mockMvc.perform(put("/admin/orders/999/ship"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
    }
}
