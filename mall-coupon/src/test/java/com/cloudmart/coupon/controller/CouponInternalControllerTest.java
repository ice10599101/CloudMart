package com.cloudmart.coupon.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.coupon.service.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CouponInternalControllerTest {

    private MockMvc mockMvc;

    private final CouponService couponService = Mockito.mock(CouponService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CouponInternalController(couponService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /coupons/expire-batch")
    class ExpireBatchTests {

        @Test
        @DisplayName("批量过期优惠券成功返回信封格式")
        void expireBatch_ShouldReturnSuccessEnvelope() throws Exception {
            given(couponService.expireBatch()).willReturn(15);

            mockMvc.perform(post("/coupons/expire-batch"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(15));
        }

        @Test
        @DisplayName("批量过期优惠券无过期数据返回0")
        void expireBatch_WhenNoExpiredCoupons_ShouldReturnZero() throws Exception {
            given(couponService.expireBatch()).willReturn(0);

            mockMvc.perform(post("/coupons/expire-batch"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(0));
        }

        @Test
        @DisplayName("批量过期优惠券服务异常返回错误信封")
        void expireBatch_WhenServiceFails_ShouldReturnErrorEnvelope() throws Exception {
            willThrow(new BusinessException("COUPON_EXPIRE_FAILED", "批量过期处理失败"))
                    .given(couponService).expireBatch();

            mockMvc.perform(post("/coupons/expire-batch"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("COUPON_EXPIRE_FAILED"));
        }
    }
}
