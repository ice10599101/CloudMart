package com.cloudmart.order.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.order.converter.OrderConverter;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.dto.OrderItemDTO;
import com.cloudmart.order.feign.PaymentFeignClient.PaymentDTO;
import com.cloudmart.order.service.OrderService;
import com.cloudmart.order.vo.OrderVO;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private OrderService orderService;
    private OrderConverter orderConverter;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        orderConverter = mock(OrderConverter.class);
        OrderController controller = new OrderController(orderService, orderConverter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private OrderDTO buildOrderDTO(String status) {
        return new OrderDTO(1L, "ORD001", new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, null, status, "张三", "13800138000", "北京市",
                null, null, null, null, List.of(), LocalDateTime.of(2026, 1, 1, 0, 0), null);
    }

    private OrderVO buildOrderVO(String status) {
        return new OrderVO(1L, "ORD001", status, new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), LocalDateTime.of(2026, 1, 1, 0, 0), null);
    }

    @Nested
    @DisplayName("GET /orders/{orderId}")
    class GetOrderByIdTests {

        @Test
        @DisplayName("own order -> returns 200 with order data")
        void getOrderById_OwnOrder_ShouldReturn200() throws Exception {
            OrderDTO dto = buildOrderDTO("PENDING_PAYMENT");
            when(orderService.getOrderById(1L, 1L)).thenReturn(dto);
            when(orderConverter.orderDtoToVO(dto)).thenReturn(buildOrderVO("PENDING_PAYMENT"));

            mockMvc.perform(get("/orders/1")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
        }
    }

    @Nested
    @DisplayName("PUT /orders/{orderId}/cancel")
    class CancelOrderTests {

        @Test
        @DisplayName("cancel pending order -> returns 200")
        void cancelOrder_PendingOrder_ShouldReturn200() throws Exception {
            OrderDTO dto = buildOrderDTO("CANCELLED");
            when(orderService.cancelOrder(1L, 1L)).thenReturn(dto);
            when(orderConverter.orderDtoToVO(dto)).thenReturn(buildOrderVO("CANCELLED"));

            mockMvc.perform(put("/orders/1/cancel")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }
    }

    @Nested
    @DisplayName("PUT /orders/{orderId}/ship")
    class ShipOrderTests {

        @Test
        @DisplayName("ship order -> returns 200")
        void shipOrder_ShouldReturn200() throws Exception {
            OrderDTO dto = buildOrderDTO("SHIPPED");
            when(orderService.shipOrder(1L)).thenReturn(dto);
            when(orderConverter.orderDtoToVO(dto)).thenReturn(buildOrderVO("SHIPPED"));

            mockMvc.perform(put("/orders/1/ship"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SHIPPED"));
        }
    }

    @Nested
    @DisplayName("PUT /orders/{orderId}/confirm")
    class ConfirmReceiptTests {

        @Test
        @DisplayName("confirm receipt -> returns 200")
        void confirmReceipt_ShouldReturn200() throws Exception {
            OrderDTO dto = buildOrderDTO("COMPLETED");
            when(orderService.confirmReceipt(1L, 1L)).thenReturn(dto);
            when(orderConverter.orderDtoToVO(dto)).thenReturn(buildOrderVO("COMPLETED"));

            mockMvc.perform(put("/orders/1/confirm")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }
    }

    @Nested
    @DisplayName("POST /orders/{orderId}/pay")
    class PayForOrderTests {

        @Test
        @DisplayName("pay for order -> returns 200 with payment info")
        void payForOrder_ShouldReturn200() throws Exception {
            PaymentDTO paymentDTO = new PaymentDTO(1L, 1L, "PAY001", new BigDecimal("100.00"), "MOCK", "PENDING", null, null, "http://pay.url");
            when(orderService.payForOrder(1L, 1L)).thenReturn(paymentDTO);

            mockMvc.perform(post("/orders/1/pay")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paymentNo").value("PAY001"));
        }
    }

    @Nested
    @DisplayName("POST /orders/{orderId}/refund")
    class RequestRefundTests {

        @Test
        @DisplayName("request refund -> returns 200")
        void requestRefund_ShouldReturn200() throws Exception {
            OrderDTO dto = buildOrderDTO("REFUNDING");
            when(orderService.requestRefund(1L, 1L, "defective")).thenReturn(dto);
            when(orderConverter.orderDtoToVO(dto)).thenReturn(buildOrderVO("REFUNDING"));

            mockMvc.perform(post("/orders/1/refund")
                            .header("X-User-Id", "1")
                            .param("refundReason", "defective"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REFUNDING"));
        }
    }

    @Nested
    @DisplayName("POST /orders/{orderId}/payment-success")
    class NotifyPaymentSuccessTests {

        @Test
        @DisplayName("notify payment success -> returns 200")
        void notifyPaymentSuccess_ShouldReturn200() throws Exception {
            mockMvc.perform(post("/orders/1/payment-success"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(orderService).notifyPaymentSuccess(1L);
        }
    }
}
