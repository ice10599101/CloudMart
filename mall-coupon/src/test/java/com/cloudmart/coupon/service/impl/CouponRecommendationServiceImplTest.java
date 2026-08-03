package com.cloudmart.coupon.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.coupon.discount.DiscountCalculator;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.vo.RecommendationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponRecommendationServiceImplTest {

    @Mock
    private CouponService couponService;

    private CouponRecommendationServiceImpl recommendationService;

    @BeforeEach
    void setUp() {
        DiscountCalculator calculator = new DiscountCalculator();
        recommendationService = new CouponRecommendationServiceImpl(couponService, calculator);
    }

    private UserCouponDTO cashCoupon(Long id, String threshold, String discount) {
        return new UserCouponDTO(
                id, 1L, 10L, "UNUSED", null,
                LocalDateTime.now(), null, LocalDateTime.now().plusDays(30),
                "满减券", "AMOUNT_OFF", new BigDecimal(threshold), new BigDecimal(discount), null);
    }

    private UserCouponDTO rateCoupon(Long id, String threshold, String rate) {
        return new UserCouponDTO(
                id, 1L, 20L, "UNUSED", null,
                LocalDateTime.now(), null, LocalDateTime.now().plusDays(30),
                "折扣券", "PERCENT_OFF", new BigDecimal(threshold), null, new BigDecimal(rate));
    }

    @Nested
    @DisplayName("recommend")
    class RecommendTests {

        @Test
        @DisplayName("无可用优惠券返回零优惠")
        void recommend_noCoupons() {
            when(couponService.listUserCoupons(anyLong(), anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            RecommendationVO vo = recommendationService.recommend(1L, new BigDecimal("200"));

            assertThat(vo.couponIds()).isEmpty();
            assertThat(vo.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(vo.finalAmount()).isEqualByComparingTo(new BigDecimal("200"));
            assertThat(vo.originalAmount()).isEqualByComparingTo(new BigDecimal("200"));
        }

        @Test
        @DisplayName("单张满减券达门槛应推荐")
        void recommend_singleCashCoupon() {
            when(couponService.listUserCoupons(eq(1L), eq("UNUSED"), anyInt(), anyInt()))
                    .thenReturn(List.of(cashCoupon(1L, "100", "20")));

            RecommendationVO vo = recommendationService.recommend(1L, new BigDecimal("200"));

            assertThat(vo.couponIds()).containsExactly(1L);
            assertThat(vo.discountAmount()).isEqualByComparingTo(new BigDecimal("20"));
            assertThat(vo.finalAmount()).isEqualByComparingTo(new BigDecimal("180"));
        }

        @Test
        @DisplayName("满减+折扣券应推荐最优组合")
        void recommend_cashAndRate() {
            when(couponService.listUserCoupons(eq(1L), eq("UNUSED"), anyInt(), anyInt()))
                    .thenReturn(List.of(
                            cashCoupon(1L, "100", "30"),
                            rateCoupon(2L, "100", "0.80")));

            RecommendationVO vo = recommendationService.recommend(1L, new BigDecimal("200"));

            assertThat(vo.couponIds()).hasSize(2);
            assertThat(vo.discountAmount()).isGreaterThan(new BigDecimal("30"));
            assertThat(vo.originalAmount()).isEqualByComparingTo(new BigDecimal("200"));
            assertThat(vo.finalAmount()).isLessThan(new BigDecimal("200"));
        }

        @Test
        @DisplayName("订单金额为零应抛异常")
        void recommend_zeroAmount() {
            assertThatThrownBy(() -> recommendationService.recommend(1L, BigDecimal.ZERO))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("VALIDATION_ERROR");
                    });
        }

        @Test
        @DisplayName("订单金额为负应抛异常")
        void recommend_negativeAmount() {
            assertThatThrownBy(() -> recommendationService.recommend(1L, new BigDecimal("-10")))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("用户ID为null应抛异常")
        void recommend_nullUserId() {
            assertThatThrownBy(() -> recommendationService.recommend(null, new BigDecimal("100")))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("未达门槛的优惠券不应推荐")
        void recommend_belowThreshold() {
            when(couponService.listUserCoupons(eq(1L), eq("UNUSED"), anyInt(), anyInt()))
                    .thenReturn(List.of(cashCoupon(1L, "500", "100")));

            RecommendationVO vo = recommendationService.recommend(1L, new BigDecimal("200"));

            assertThat(vo.couponIds()).isEmpty();
            assertThat(vo.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("无法识别的券类型应跳过")
        void recommend_unknownTypeSkipped() {
            UserCouponDTO unknownCoupon = new UserCouponDTO(
                    1L, 1L, 30L, "UNUSED", null,
                    LocalDateTime.now(), null, LocalDateTime.now().plusDays(30),
                    "神秘券", "MYSTERY", new BigDecimal("100"), null, null);

            when(couponService.listUserCoupons(eq(1L), eq("UNUSED"), anyInt(), anyInt()))
                    .thenReturn(List.of(unknownCoupon));

            RecommendationVO vo = recommendationService.recommend(1L, new BigDecimal("200"));

            assertThat(vo.couponIds()).isEmpty();
            assertThat(vo.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
