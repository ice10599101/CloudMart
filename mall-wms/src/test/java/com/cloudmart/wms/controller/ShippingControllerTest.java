package com.cloudmart.wms.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.wms.dto.CreateShippingRequest;
import com.cloudmart.wms.service.ShippingService;
import com.cloudmart.wms.vo.ShippingOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShippingControllerTest {

    private MockMvc mockMvc;

    private final ShippingService shippingService = Mockito.mock(ShippingService.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ShippingController(shippingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("创建物流订单 - 成功返回信封")
    void createShippingOrder_ShouldReturnEnvelope() throws Exception {
        ShippingOrderVO vo = new ShippingOrderVO(1L, 100L, "SF123456", "顺丰", "PENDING", null);

        given(shippingService.createShipping(Mockito.any(CreateShippingRequest.class))).willReturn(vo);

        mockMvc.perform(post("/shipping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":100,\"warehouseId\":1,\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"receiverAddress\":\"北京市\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.orderId").value(100))
                .andExpect(jsonPath("$.data.carrier").value("顺丰"));
    }

    @Test
    @DisplayName("根据订单ID查询物流 - 成功返回信封")
    void getByOrderId_ShouldReturnEnvelope() throws Exception {
        ShippingOrderVO vo = new ShippingOrderVO(1L, 100L, "SF123456", "顺丰", "SHIPPED", FIXED_TIME);

        given(shippingService.getByOrderId(100L)).willReturn(vo);

        mockMvc.perform(get("/shipping/order/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("更新物流状态 - 成功返回信封")
    void updateStatus_ShouldReturnEnvelope() throws Exception {
        ShippingOrderVO vo = new ShippingOrderVO(1L, 100L, "SF123456", "顺丰", "DELIVERED", FIXED_TIME);

        given(shippingService.updateStatus(1L, "DELIVERED")).willReturn(vo);

        mockMvc.perform(put("/shipping/1/status")
                        .param("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("根据订单ID查询物流 - 不存在时返回错误信封")
    void getByOrderId_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(shippingService.getByOrderId(999L))
                .willThrow(new BusinessException("WMS_SERVICE_UNAVAILABLE", "物流订单不存在"));

        mockMvc.perform(get("/shipping/order/999"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("WMS_SERVICE_UNAVAILABLE"));
    }
}
