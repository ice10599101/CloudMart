package com.cloudmart.coupon.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.coupon.converter.UserCouponConverter;
import com.cloudmart.coupon.dto.ReturnCouponRequest;
import com.cloudmart.coupon.dto.UseCouponRequest;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.service.CouponRecommendationService;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.service.ExchangeCodeService;
import com.cloudmart.coupon.vo.RecommendationVO;
import com.cloudmart.coupon.vo.UserCouponVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CouponUserControllerTest {

    private MockMvc mockMvc;

    private final CouponService couponService = Mockito.mock(CouponService.class);
    private final CouponRecommendationService couponRecommendationService = Mockito.mock(CouponRecommendationService.class);
    private final ExchangeCodeService exchangeCodeService = Mockito.mock(ExchangeCodeService.class);
    private final UserCouponConverter userCouponConverter = Mockito.mock(UserCouponConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CouponUserController(couponService, couponRecommendationService, exchangeCodeService, userCouponConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("领取优惠券 - 成功返回信封格式")
    void claimCoupon_ShouldReturn200WithEnvelope() throws Exception {
        UserCouponDTO dto = new UserCouponDTO(1L, 1L, 10L, "UNUSED", null,
                LocalDateTime.now(), null, LocalDateTime.now().plusDays(30),
                "满100减20", "AMOUNT_OFF", new BigDecimal("100.00"), new BigDecimal("20.00"), null);

        given(couponService.claimCoupon(1L, 10L)).willReturn(dto);

        UserCouponVO vo = new UserCouponVO(1L, 10L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("20.00"), new BigDecimal("100.00"), "UNUSED", null, null);
        given(userCouponConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/user-coupons/claim")
                        .header("X-User-Id", 1)
                        .param("templateId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("UNUSED"));
    }

    @Test
    @DisplayName("查询用户优惠券列表 - 成功返回信封格式带分页")
    void listUserCoupons_ShouldReturn200WithMeta() throws Exception {
        UserCouponDTO dto = new UserCouponDTO(1L, 1L, 10L, "UNUSED", null,
                LocalDateTime.now(), null, LocalDateTime.now().plusDays(30),
                "满100减20", "AMOUNT_OFF", new BigDecimal("100.00"), new BigDecimal("20.00"), null);

        given(couponService.listUserCoupons(1L, null, 1, 20)).willReturn(List.of(dto));
        given(couponService.countUserCoupons(1L, null)).willReturn(1L);

        UserCouponVO vo = new UserCouponVO(1L, 10L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("20.00"), new BigDecimal("100.00"), "UNUSED", null, null);
        given(userCouponConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/user-coupons")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("查询用户优惠券详情 - 成功返回信封格式")
    void getUserCouponById_ShouldReturn200WithEnvelope() throws Exception {
        UserCouponDTO dto = new UserCouponDTO(1L, 1L, 10L, "UNUSED", null,
                LocalDateTime.now(), null, LocalDateTime.now().plusDays(30),
                "满100减20", "AMOUNT_OFF", new BigDecimal("100.00"), new BigDecimal("20.00"), null);

        given(couponService.getUserCouponById(1L)).willReturn(dto);

        UserCouponVO vo = new UserCouponVO(1L, 10L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("20.00"), new BigDecimal("100.00"), "UNUSED", null, null);
        given(userCouponConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/user-coupons/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("使用优惠券 - 成功返回信封格式")
    void useCoupon_ShouldReturn200WithEnvelope() throws Exception {
        willDoNothing().given(couponService).useCoupon(1L, 100L);

        mockMvc.perform(post("/user-coupons/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCouponId\":1,\"orderId\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("退还优惠券 - 成功返回信封格式")
    void returnCoupon_ShouldReturn200WithEnvelope() throws Exception {
        willDoNothing().given(couponService).returnCoupon(1L, 100L);

        mockMvc.perform(post("/user-coupons/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCouponId\":1,\"orderId\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("领取优惠券 - 库存不足返回错误信封")
    void claimCoupon_WhenOutOfStock_ShouldReturnErrorEnvelope() throws Exception {
        given(couponService.claimCoupon(1L, 999L))
                .willThrow(new BusinessException("COUPON_OUT_OF_STOCK", "优惠券已领完"));

        mockMvc.perform(post("/user-coupons/claim")
                        .header("X-User-Id", 1)
                        .param("templateId", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COUPON_OUT_OF_STOCK"))
                .andExpect(jsonPath("$.error.message").value("优惠券已领完"));
    }

    @Test
    @DisplayName("使用优惠券 - 缺少必填字段返回校验错误")
    void useCoupon_WhenMissingRequiredFields_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/user-coupons/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("优惠券推荐 - 成功返回推荐方案")
    void recommendCoupons_ShouldReturn200WithRecommendation() throws Exception {
        RecommendationVO vo = new RecommendationVO(
                List.of(1L, 2L),
                new BigDecimal("200.00"),
                new BigDecimal("50.00"),
                new BigDecimal("150.00")
        );
        given(couponRecommendationService.recommend(1L, new BigDecimal("200.00"))).willReturn(vo);

        mockMvc.perform(get("/user-coupons/recommend")
                        .header("X-User-Id", 1)
                        .param("orderAmount", "200.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.couponIds").isArray())
                .andExpect(jsonPath("$.data.couponIds[0]").value(1))
                .andExpect(jsonPath("$.data.couponIds[1]").value(2))
                .andExpect(jsonPath("$.data.originalAmount").value(200.00))
                .andExpect(jsonPath("$.data.discountAmount").value(50.00))
                .andExpect(jsonPath("$.data.finalAmount").value(150.00));
    }

    @Test
    @DisplayName("兑换码兑换 - 成功返回用户券ID")
    void exchangeCode_ShouldReturn200WithUserCouponId() throws Exception {
        given(exchangeCodeService.exchange(1L, "ABCDEF")).willReturn(100L);

        mockMvc.perform(post("/user-coupons/exchange")
                        .header("X-User-Id", 1)
                        .param("code", "ABCDEF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(100));
    }

    @Test
    @DisplayName("兑换码兑换 - 兑换码已使用返回错误信封")
    void exchangeCode_WhenAlreadyUsed_ShouldReturnErrorEnvelope() throws Exception {
        given(exchangeCodeService.exchange(1L, "ABCDEF"))
                .willThrow(new BusinessException("EXCHANGE_CODE_ALREADY_USED", "兑换码已被使用"));

        mockMvc.perform(post("/user-coupons/exchange")
                        .header("X-User-Id", 1)
                        .param("code", "ABCDEF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EXCHANGE_CODE_ALREADY_USED"));
    }
}
