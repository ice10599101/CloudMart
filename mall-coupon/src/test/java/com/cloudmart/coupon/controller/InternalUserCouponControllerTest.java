package com.cloudmart.coupon.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.coupon.converter.UserCouponConverter;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.vo.UserCouponVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T03：订单服务专用券事实查询（/internal/user-coupons/{id}）。
 * 角色守卫由过滤器 + @PreAuthorize 保证，这里覆盖内部契约与不存在语义。
 */
class InternalUserCouponControllerTest {

    private MockMvc mockMvc;

    private final CouponService couponService = Mockito.mock(CouponService.class);
    private final UserCouponConverter userCouponConverter = Mockito.mock(UserCouponConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalUserCouponController(couponService, userCouponConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("内部查询用户优惠券 - 成功返回信封")
    void getById_ShouldReturnEnvelope() throws Exception {
        UserCouponDTO dto = new UserCouponDTO(1L, 9L, 10L, "RESERVED", 100L,
                LocalDateTime.now(), null, LocalDateTime.now().plusDays(30),
                "满100减20", "AMOUNT_OFF", new BigDecimal("100.00"), new BigDecimal("20.00"), null);
        UserCouponVO vo = new UserCouponVO(1L, 9L, 10L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("20.00"), null, new BigDecimal("100.00"), "RESERVED", 100L, null, null, null);

        given(couponService.getUserCouponById(1L)).willReturn(dto);
        given(userCouponConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/internal/user-coupons/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }

    @Test
    @DisplayName("内部查询不存在的券 - 404")
    void getById_WhenMissing_ShouldReturn404() throws Exception {
        given(couponService.getUserCouponById(404L))
                .willThrow(new BusinessException("USER_COUPON_NOT_FOUND", "用户优惠券不存在"));

        mockMvc.perform(get("/internal/user-coupons/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_COUPON_NOT_FOUND"));
    }
}
