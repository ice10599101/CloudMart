package com.cloudmart.coupon.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.coupon.converter.CouponTemplateConverter;
import com.cloudmart.coupon.converter.UserCouponConverter;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.entity.CouponTemplate;
import com.cloudmart.coupon.entity.UserCoupon;
import com.cloudmart.coupon.repository.CouponTemplateMapper;
import com.cloudmart.coupon.repository.UserCouponMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponServiceImplTest {

    private CouponTemplateMapper couponTemplateMapper;
    private UserCouponMapper userCouponMapper;
    private CouponTemplateConverter couponTemplateConverter;
    private UserCouponConverter userCouponConverter;
    private RedissonClient redissonClient;
    private StringRedisTemplate redisTemplate;
    private RLock rLock;
    private ValueOperations<String, String> valueOperations;
    private CouponServiceImpl couponService;

    private static final Long USER_ID = 1001L;
    private static final Long TEMPLATE_ID = 2001L;
    private static final Long ORDER_ID = 3001L;
    private static final Long USER_COUPON_ID = 4001L;

    private CouponTemplate enabledTemplate;

    @BeforeEach
    void setUp() {
        couponTemplateMapper = mock(CouponTemplateMapper.class);
        userCouponMapper = mock(UserCouponMapper.class);
        couponTemplateConverter = mock(CouponTemplateConverter.class);
        userCouponConverter = mock(UserCouponConverter.class);
        redissonClient = mock(RedissonClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        rLock = mock(RLock.class);
        valueOperations = mock(ValueOperations.class);

        couponService = new CouponServiceImpl(
                couponTemplateMapper, userCouponMapper, couponTemplateConverter,
                userCouponConverter, redissonClient, redisTemplate
        );

        enabledTemplate = new CouponTemplate();
        enabledTemplate.setId(TEMPLATE_ID);
        enabledTemplate.setName("Test Coupon");
        enabledTemplate.setType("AMOUNT_OFF");
        enabledTemplate.setThresholdAmount(new BigDecimal("100"));
        enabledTemplate.setDiscountAmount(new BigDecimal("20"));
        enabledTemplate.setTotalQuantity(100);
        enabledTemplate.setRemainingQuantity(50);
        enabledTemplate.setPerUserLimit(1);
        enabledTemplate.setValidityType("FIXED_DATE");
        enabledTemplate.setStartTime(LocalDateTime.now().minusDays(1));
        enabledTemplate.setEndTime(LocalDateTime.now().plusDays(7));
        enabledTemplate.setStatus("ENABLED");
    }

    private void mockLockAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Nested
    @DisplayName("claimCoupon")
    class ClaimCouponTests {

        @Test
        @DisplayName("should claim coupon successfully")
        void claimCoupon_success() throws InterruptedException {
            mockLockAcquired();
            when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(enabledTemplate);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("coupon:stock:" + TEMPLATE_ID)).thenReturn("50");
            when(userCouponMapper.selectCount(any())).thenReturn(0L);
            when(valueOperations.decrement("coupon:stock:" + TEMPLATE_ID)).thenReturn(49L);
            when(couponTemplateMapper.update(any(), any())).thenReturn(1);
            when(userCouponMapper.insert(any(UserCoupon.class))).thenReturn(1);
            when(userCouponConverter.toDTO(any(UserCoupon.class), any(CouponTemplate.class)))
                    .thenReturn(new UserCouponDTO(
                            USER_COUPON_ID, USER_ID, TEMPLATE_ID, "UNUSED",
                            null, LocalDateTime.now(), null, enabledTemplate.getEndTime(),
                            "Test Coupon", "AMOUNT_OFF", new BigDecimal("100"),
                            new BigDecimal("20"), null
                    ));

            UserCouponDTO result = couponService.claimCoupon(USER_ID, TEMPLATE_ID);

            assertThat(result.status()).isEqualTo("UNUSED");
            assertThat(result.templateName()).isEqualTo("Test Coupon");
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("should throw when template not found")
        void claimCoupon_templateNotFound_throwsException() throws InterruptedException {
            mockLockAcquired();
            when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(null);

            assertThatThrownBy(() -> couponService.claimCoupon(USER_ID, TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("TEMPLATE_NOT_FOUND"));
        }

        @Test
        @DisplayName("should throw when template is disabled")
        void claimCoupon_templateDisabled_throwsException() throws InterruptedException {
            mockLockAcquired();
            enabledTemplate.setStatus("DISABLED");
            when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(enabledTemplate);

            assertThatThrownBy(() -> couponService.claimCoupon(USER_ID, TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("TEMPLATE_DISABLED"));
        }

        @Test
        @DisplayName("should throw when coupon is expired")
        void claimCoupon_couponExpired_throwsException() throws InterruptedException {
            mockLockAcquired();
            enabledTemplate.setEndTime(LocalDateTime.now().minusDays(1));
            when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(enabledTemplate);

            assertThatThrownBy(() -> couponService.claimCoupon(USER_ID, TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("TEMPLATE_EXPIRED"));
        }

        @Test
        @DisplayName("should throw when coupon stock is exhausted")
        void claimCoupon_stockExhausted_throwsException() throws InterruptedException {
            mockLockAcquired();
            when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(enabledTemplate);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("coupon:stock:" + TEMPLATE_ID)).thenReturn("0");

            assertThatThrownBy(() -> couponService.claimCoupon(USER_ID, TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("STOCK_INSUFFICIENT"));
        }

        @Test
        @DisplayName("should throw when user already claimed max coupons")
        void claimCoupon_alreadyClaimedMax_throwsException() throws InterruptedException {
            mockLockAcquired();
            when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(enabledTemplate);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("coupon:stock:" + TEMPLATE_ID)).thenReturn("50");
            when(userCouponMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> couponService.claimCoupon(USER_ID, TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CLAIM_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("should throw when Redis decrement goes below zero and rollback")
        void claimCoupon_decrementBelowZero_rollbacksAndThrows() throws InterruptedException {
            mockLockAcquired();
            when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(enabledTemplate);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("coupon:stock:" + TEMPLATE_ID)).thenReturn("1");
            when(userCouponMapper.selectCount(any())).thenReturn(0L);
            when(valueOperations.decrement("coupon:stock:" + TEMPLATE_ID)).thenReturn(-1L);

            assertThatThrownBy(() -> couponService.claimCoupon(USER_ID, TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("STOCK_INSUFFICIENT"));
            verify(valueOperations).increment("coupon:stock:" + TEMPLATE_ID);
        }

        @Test
        @DisplayName("should throw when DB stock update fails and rollback Redis")
        void claimCoupon_dbStockUpdateFails_rollbacksRedis() throws InterruptedException {
            mockLockAcquired();
            when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(enabledTemplate);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("coupon:stock:" + TEMPLATE_ID)).thenReturn("50");
            when(userCouponMapper.selectCount(any())).thenReturn(0L);
            when(valueOperations.decrement("coupon:stock:" + TEMPLATE_ID)).thenReturn(49L);
            when(couponTemplateMapper.update(any(), any())).thenReturn(0);

            assertThatThrownBy(() -> couponService.claimCoupon(USER_ID, TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("STOCK_INSUFFICIENT"));
            verify(valueOperations).increment("coupon:stock:" + TEMPLATE_ID);
        }
    }

    @Nested
    @DisplayName("useCoupon")
    class UseCouponTests {

        @Test
        @DisplayName("should use coupon successfully")
        void useCoupon_success() {
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setId(USER_COUPON_ID);
            userCoupon.setUserId(USER_ID);
            userCoupon.setTemplateId(TEMPLATE_ID);
            userCoupon.setStatus("UNUSED");
            userCoupon.setExpiredAt(LocalDateTime.now().plusDays(7));

            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(userCoupon);
            when(userCouponMapper.updateStatusIfMatch(
                    eq(USER_COUPON_ID), eq("UNUSED"), eq("USED"), eq(ORDER_ID), any(LocalDateTime.class)
            )).thenReturn(1);

            couponService.useCoupon(USER_COUPON_ID, ORDER_ID);

            verify(userCouponMapper).updateStatusIfMatch(
                    eq(USER_COUPON_ID), eq("UNUSED"), eq("USED"), eq(ORDER_ID), any(LocalDateTime.class)
            );
        }

        @Test
        @DisplayName("should throw when coupon already used")
        void useCoupon_alreadyUsed_throwsException() {
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setId(USER_COUPON_ID);
            userCoupon.setStatus("USED");

            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(userCoupon);

            assertThatThrownBy(() -> couponService.useCoupon(USER_COUPON_ID, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("COUPON_STATUS_ERROR"));
        }

        @Test
        @DisplayName("should throw when coupon is expired")
        void useCoupon_expired_throwsException() {
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setId(USER_COUPON_ID);
            userCoupon.setStatus("UNUSED");
            userCoupon.setExpiredAt(LocalDateTime.now().minusDays(1));

            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(userCoupon);

            assertThatThrownBy(() -> couponService.useCoupon(USER_COUPON_ID, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("COUPON_EXPIRED"));
        }

        @Test
        @DisplayName("should throw when coupon not found")
        void useCoupon_notFound_throwsException() {
            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(null);

            assertThatThrownBy(() -> couponService.useCoupon(USER_COUPON_ID, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_COUPON_NOT_FOUND"));
        }

        @Test
        @DisplayName("should throw when concurrent status change detected")
        void useCoupon_concurrentChange_throwsException() {
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setId(USER_COUPON_ID);
            userCoupon.setStatus("UNUSED");
            userCoupon.setExpiredAt(LocalDateTime.now().plusDays(7));

            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(userCoupon);
            when(userCouponMapper.updateStatusIfMatch(
                    eq(USER_COUPON_ID), eq("UNUSED"), eq("USED"), eq(ORDER_ID), any(LocalDateTime.class)
            )).thenReturn(0);

            assertThatThrownBy(() -> couponService.useCoupon(USER_COUPON_ID, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("COUPON_STATUS_ERROR"));
        }
    }

    @Nested
    @DisplayName("returnCoupon")
    class ReturnCouponTests {

        @Test
        @DisplayName("should return coupon successfully")
        void returnCoupon_success() {
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setId(USER_COUPON_ID);
            userCoupon.setStatus("USED");
            userCoupon.setOrderId(ORDER_ID);

            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(userCoupon);
            when(userCouponMapper.returnCouponIfMatch(
                    eq(USER_COUPON_ID), eq("USED"), eq("UNUSED"), eq(ORDER_ID)
            )).thenReturn(1);

            couponService.returnCoupon(USER_COUPON_ID, ORDER_ID);

            verify(userCouponMapper).returnCouponIfMatch(
                    eq(USER_COUPON_ID), eq("USED"), eq("UNUSED"), eq(ORDER_ID)
            );
        }

        @Test
        @DisplayName("should throw when coupon is not in USED status")
        void returnCoupon_notUsed_throwsException() {
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setId(USER_COUPON_ID);
            userCoupon.setStatus("UNUSED");

            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(userCoupon);

            assertThatThrownBy(() -> couponService.returnCoupon(USER_COUPON_ID, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("COUPON_STATUS_ERROR"));
        }

        @Test
        @DisplayName("should throw when order ID does not match")
        void returnCoupon_orderMismatch_throwsException() {
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setId(USER_COUPON_ID);
            userCoupon.setStatus("USED");
            userCoupon.setOrderId(9999L);

            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(userCoupon);

            assertThatThrownBy(() -> couponService.returnCoupon(USER_COUPON_ID, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_MISMATCH"));
        }

        @Test
        @DisplayName("should throw when coupon not found")
        void returnCoupon_notFound_throwsException() {
            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(null);

            assertThatThrownBy(() -> couponService.returnCoupon(USER_COUPON_ID, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_COUPON_NOT_FOUND"));
        }
    }
}
