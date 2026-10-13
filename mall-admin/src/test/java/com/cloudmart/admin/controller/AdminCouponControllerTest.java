package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.CouponTemplateDTO;
import com.cloudmart.admin.dto.feign.CreateCouponTemplateRequest;
import com.cloudmart.admin.feign.CouponFeignClient;
import com.cloudmart.admin.feign.ExchangeCodeFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCouponControllerTest {

    private MockMvc mockMvc;
    private CouponFeignClient couponFeignClient;

    @BeforeEach
    void setUp() {
        couponFeignClient = mock(CouponFeignClient.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminCouponController(couponFeignClient, mock(ExchangeCodeFeignClient.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listCoupons_returnsCouponList() throws Exception {
        given(couponFeignClient.listTemplates(any(), any(), any(), any()))
                .willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/coupons").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getCoupon_returnsCouponDetail() throws Exception {
        CouponTemplateDTO coupon = new CouponTemplateDTO(1L, "满100减20", "FIXED",
                new BigDecimal("100"), new BigDecimal("20"), null, 1000, 800, 1,
                "FIXED_DATE", LocalDateTime.now(), LocalDateTime.now().plusDays(30),
                null, "ACTIVE", LocalDateTime.now());
        given(couponFeignClient.getTemplateById(1L))
                .willReturn(ApiResponse.ok(coupon));

        mockMvc.perform(get("/business/coupons/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createCoupon_couponCreatedSuccessfully() throws Exception {
        CouponTemplateDTO coupon = new CouponTemplateDTO(1L, "满100减20", "FIXED",
                new BigDecimal("100"), new BigDecimal("20"), null, 1000, 800, 1,
                "FIXED_DATE", LocalDateTime.now(), LocalDateTime.now().plusDays(30),
                null, "ACTIVE", LocalDateTime.now());
        given(couponFeignClient.createTemplate(any(CreateCouponTemplateRequest.class)))
                .willReturn(ApiResponse.ok(coupon));

        mockMvc.perform(post("/business/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"满100减20\",\"type\":\"FIXED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void disableCoupon_couponDisabledSuccessfully() throws Exception {
        CouponTemplateDTO coupon = new CouponTemplateDTO(1L, "满100减20", "FIXED",
                new BigDecimal("100"), new BigDecimal("20"), null, 1000, 800, 1,
                "FIXED_DATE", LocalDateTime.now(), LocalDateTime.now().plusDays(30),
                null, "DISABLED", LocalDateTime.now());
        given(couponFeignClient.disableTemplate(1L))
                .willReturn(ApiResponse.ok(coupon));

        mockMvc.perform(put("/business/coupons/1/disable").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void enableCoupon_couponEnabledSuccessfully() throws Exception {
        CouponTemplateDTO coupon = new CouponTemplateDTO(1L, "满100减20", "FIXED",
                new BigDecimal("100"), new BigDecimal("20"), null, 1000, 800, 1,
                "FIXED_DATE", LocalDateTime.now(), LocalDateTime.now().plusDays(30),
                null, "ACTIVE", LocalDateTime.now());
        given(couponFeignClient.enableTemplate(1L))
                .willReturn(ApiResponse.ok(coupon));

        mockMvc.perform(put("/business/coupons/1/enable").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
