package com.cloudmart.coupon.discount;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountCalculatorTest {

    private final DiscountCalculator calculator = new DiscountCalculator();

    private Discount cash(long couponId, String threshold, String discount) {
        return new CashDiscount(couponId, new BigDecimal(threshold), new BigDecimal(discount));
    }

    private Discount rate(long couponId, String threshold, String rate) {
        return new RateDiscount(couponId, new BigDecimal(threshold), new BigDecimal(rate));
    }

    @Nested
    @DisplayName("calculateBestDiscount")
    class CalculateBestDiscountTests {

        @Test
        @DisplayName("空列表返回零优惠")
        void emptyDiscounts() {
            DiscountResult result = calculator.calculateBestDiscount(new BigDecimal("100"), List.of());
            assertThat(result.totalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.finalAmount()).isEqualByComparingTo(new BigDecimal("100"));
            assertThat(result.appliedDiscounts()).isEmpty();
        }

        @Test
        @DisplayName("null列表返回零优惠")
        void nullDiscounts() {
            DiscountResult result = calculator.calculateBestDiscount(new BigDecimal("100"), null);
            assertThat(result.totalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("单张满减券达门槛应应用")
        void singleCashDiscount_aboveThreshold() {
            DiscountResult result = calculator.calculateBestDiscount(
                    new BigDecimal("200"), List.of(cash(1, "100", "20")));
            assertThat(result.totalDiscount()).isEqualByComparingTo(new BigDecimal("20"));
            assertThat(result.finalAmount()).isEqualByComparingTo(new BigDecimal("180"));
            assertThat(result.appliedDiscounts()).hasSize(1);
            assertThat(result.appliedDiscounts().get(0).getCouponId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("单张满减券未达门槛不应用")
        void singleCashDiscount_belowThreshold() {
            DiscountResult result = calculator.calculateBestDiscount(
                    new BigDecimal("50"), List.of(cash(1, "100", "20")));
            assertThat(result.totalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.appliedDiscounts()).isEmpty();
        }

        @Test
        @DisplayName("单张折扣券应按比例计算")
        void singleRateDiscount() {
            DiscountResult result = calculator.calculateBestDiscount(
                    new BigDecimal("200"), List.of(rate(1, "100", "0.85")));
            assertThat(result.totalDiscount()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(result.finalAmount()).isEqualByComparingTo(new BigDecimal("170.00"));
        }

        @Test
        @DisplayName("两张满减券叠加应选择最优顺序")
        void twoCashDiscounts_stacked() {
            DiscountResult result = calculator.calculateBestDiscount(
                    new BigDecimal("300"), List.of(
                            cash(1, "100", "20"),
                            cash(2, "200", "50")));
            assertThat(result.totalDiscount()).isEqualByComparingTo(new BigDecimal("70"));
            assertThat(result.appliedDiscounts()).hasSize(2);
        }

        @Test
        @DisplayName("满减+折扣叠加应选择最优顺序")
        void cashAndRate_stacked() {
            Discount d1 = cash(1, "100", "30");
            Discount d2 = rate(2, "100", "0.80");

            DiscountResult result = calculator.calculateBestDiscount(new BigDecimal("200"), List.of(d1, d2));

            assertThat(result.totalDiscount()).isGreaterThan(new BigDecimal("30"));
            assertThat(result.appliedDiscounts()).hasSize(2);
        }

        @Test
        @DisplayName("满减后金额不足折扣门槛时计算器应选择更优顺序")
        void cashReducesBelowRateThreshold() {
            Discount d1 = cash(1, "100", "80");
            Discount d2 = rate(2, "150", "0.80");

            // Order=200:
            // [d1,d2]: d1 applies (200→120, discount=80), d2 skipped (120<150) → total=80
            // [d2,d1]: d2 applies (200→160, discount=40), d1 applies (160→80, discount=80) → total=120
            DiscountResult result = calculator.calculateBestDiscount(new BigDecimal("200"), List.of(d1, d2));

            assertThat(result.totalDiscount()).isEqualByComparingTo(new BigDecimal("120"));
            assertThat(result.appliedDiscounts()).hasSize(2);
        }

        @Test
        @DisplayName("所有券均不达门槛时返回零优惠")
        void allDiscountsBelowThreshold() {
            Discount d1 = cash(1, "500", "100");
            Discount d2 = rate(2, "500", "0.50");

            DiscountResult result = calculator.calculateBestDiscount(new BigDecimal("200"), List.of(d1, d2));

            assertThat(result.totalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.appliedDiscounts()).isEmpty();
        }

        @Test
        @DisplayName("超过5张券时只取前5张参与排列")
        void moreThanFiveDiscounts_capped() {
            List<Discount> discounts = List.of(
                    cash(1, "0", "10"),
                    cash(2, "0", "10"),
                    cash(3, "0", "10"),
                    cash(4, "0", "10"),
                    cash(5, "0", "10"),
                    cash(6, "0", "100"));

            DiscountResult result = calculator.calculateBestDiscount(new BigDecimal("1000"), discounts);

            assertThat(result.appliedDiscounts()).hasSizeLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("多券并行计算结果与串行一致")
        void parallelConsistency() {
            List<Discount> discounts = List.of(
                    cash(1, "100", "30"),
                    rate(2, "100", "0.90"),
                    cash(3, "200", "50"),
                    rate(4, "150", "0.85"),
                    cash(5, "50", "10"));

            DiscountResult result = calculator.calculateBestDiscount(new BigDecimal("500"), discounts);

            assertThat(result.totalDiscount()).isPositive();
            assertThat(result.finalAmount()).isPositive();
            assertThat(result.finalAmount().add(result.totalDiscount()))
                    .isEqualByComparingTo(new BigDecimal("500"));
        }

        @Test
        @DisplayName("满减金额不超过当前订单金额")
        void cashDiscountCappedAtOrderAmount() {
            DiscountResult result = calculator.calculateBestDiscount(
                    new BigDecimal("50"), List.of(cash(1, "0", "100")));
            assertThat(result.totalDiscount()).isEqualByComparingTo(new BigDecimal("50"));
            assertThat(result.finalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
