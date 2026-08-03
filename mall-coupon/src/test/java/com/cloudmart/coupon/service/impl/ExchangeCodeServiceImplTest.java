package com.cloudmart.coupon.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.entity.CouponTemplate;
import com.cloudmart.coupon.entity.ExchangeCode;
import com.cloudmart.coupon.repository.CouponTemplateMapper;
import com.cloudmart.coupon.repository.ExchangeCodeMapper;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.util.CodeGenerator;
import com.cloudmart.coupon.vo.ExchangeCodeVO.BatchGenerateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * ExchangeCodeServiceImpl 单元测试
 * <p>
 * 重点覆盖 BitMap 防重兑、DB CAS 状态流转、补偿回滚等核心流程。
 * </p>
 */
@DisplayName("ExchangeCodeServiceImpl 兑换码服务测试")
@ExtendWith(MockitoExtension.class)
class ExchangeCodeServiceImplTest {

    private static final Long TEMPLATE_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final String CODE = "ABCDEFGH";
    private static final Integer SERIAL_NUMBER = 5;

    @Mock private ExchangeCodeMapper exchangeCodeMapper;
    @Mock private CouponTemplateMapper couponTemplateMapper;
    @Mock private CouponService couponService;
    @Mock private CodeGenerator codeGenerator;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private ExchangeCodeServiceImpl exchangeCodeService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private CouponTemplate enabledTemplate() {
        CouponTemplate template = new CouponTemplate();
        template.setId(TEMPLATE_ID);
        template.setName("测试券");
        template.setStatus("ENABLED");
        return template;
    }

    private ExchangeCode unusedCode() {
        ExchangeCode code = new ExchangeCode();
        code.setId(1L);
        code.setCode(CODE);
        code.setTemplateId(TEMPLATE_ID);
        code.setSerialNumber(SERIAL_NUMBER);
        code.setStatus("UNUSED");
        return code;
    }

    private UserCouponDTO userCouponDTO() {
        return new UserCouponDTO(100L, USER_ID, TEMPLATE_ID, "UNUSED", null,
                LocalDateTime.now(), null, LocalDateTime.now().plusDays(30),
                "测试券", "AMOUNT_OFF", new BigDecimal("100.00"), new BigDecimal("20.00"), null);
    }

    @Nested
    @DisplayName("generateBatch 批量生成")
    class GenerateBatchTests {

        @Test
        @DisplayName("成功批量生成兑换码")
        void shouldGenerateBatchSuccessfully() {
            given(couponTemplateMapper.selectById(TEMPLATE_ID)).willReturn(enabledTemplate());
            given(valueOperations.increment(anyString())).willReturn(1L, 2L, 3L);
            given(codeGenerator.generate(1L)).willReturn("CODE1");
            given(codeGenerator.generate(2L)).willReturn("CODE2");
            given(codeGenerator.generate(3L)).willReturn("CODE3");

            BatchGenerateResult result = exchangeCodeService.generateBatch(TEMPLATE_ID, 3);

            assertEquals(3, result.count());
            assertEquals(TEMPLATE_ID, result.templateId());
            assertNotNull(result.batchNo());
            assertEquals(List.of("CODE1", "CODE2", "CODE3"), result.codes());
            then(exchangeCodeMapper).should(times(3)).insert(any(ExchangeCode.class));
            then(redisTemplate).should().expire(anyString(), eq(Duration.ofDays(365)));
        }

        @Test
        @DisplayName("模板不存在时抛出异常")
        void shouldThrowWhenTemplateNotFound() {
            given(couponTemplateMapper.selectById(TEMPLATE_ID)).willReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.generateBatch(TEMPLATE_ID, 10));
            assertEquals("TEMPLATE_NOT_FOUND", ex.getCode());
        }

        @Test
        @DisplayName("模板已禁用时抛出异常")
        void shouldThrowWhenTemplateDisabled() {
            CouponTemplate template = enabledTemplate();
            template.setStatus("DISABLED");
            given(couponTemplateMapper.selectById(TEMPLATE_ID)).willReturn(template);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.generateBatch(TEMPLATE_ID, 10));
            assertEquals("TEMPLATE_DISABLED", ex.getCode());
        }
    }

    @Nested
    @DisplayName("exchange 兑换")
    class ExchangeTests {

        @Test
        @DisplayName("成功兑换返回用户券ID")
        void shouldExchangeSuccessfully() {
            given(codeGenerator.validate(CODE)).willReturn(true);
            given(exchangeCodeMapper.selectOne(any())).willReturn(unusedCode());
            given(valueOperations.setBit(anyString(), anyLong(), eq(true))).willReturn(false);
            given(redisTemplate.getExpire(anyString())).willReturn(-1L);
            given(exchangeCodeMapper.markExchangedIfUnused(eq(CODE), eq(USER_ID), any(LocalDateTime.class)))
                    .willReturn(1);
            given(couponService.claimCoupon(USER_ID, TEMPLATE_ID)).willReturn(userCouponDTO());

            Long userCouponId = exchangeCodeService.exchange(USER_ID, CODE);

            assertEquals(100L, userCouponId);
            then(redisTemplate).should().expire(anyString(), eq(Duration.ofDays(365)));
        }

        @Test
        @DisplayName("用户ID非法抛出异常")
        void shouldThrowForInvalidUserId() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.exchange(0L, CODE));
            assertEquals("VALIDATION_ERROR", ex.getCode());
        }

        @Test
        @DisplayName("兑换码格式非法抛出异常")
        void shouldThrowForInvalidCodeFormat() {
            given(codeGenerator.validate(CODE)).willReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.exchange(USER_ID, CODE));
            assertEquals("INVALID_CODE_FORMAT", ex.getCode());
        }

        @Test
        @DisplayName("兑换码不存在抛出异常")
        void shouldThrowWhenCodeNotFound() {
            given(codeGenerator.validate(CODE)).willReturn(true);
            given(exchangeCodeMapper.selectOne(any())).willReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.exchange(USER_ID, CODE));
            assertEquals("EXCHANGE_CODE_NOT_FOUND", ex.getCode());
        }

        @Test
        @DisplayName("兑换码已作废抛出异常")
        void shouldThrowWhenCodeDisabled() {
            ExchangeCode code = unusedCode();
            code.setStatus("DISABLED");
            given(codeGenerator.validate(CODE)).willReturn(true);
            given(exchangeCodeMapper.selectOne(any())).willReturn(code);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.exchange(USER_ID, CODE));
            assertEquals("EXCHANGE_CODE_DISABLED", ex.getCode());
        }

        @Test
        @DisplayName("兑换码已兑换（DB状态）抛出异常")
        void shouldThrowWhenCodeAlreadyExchangedInDb() {
            ExchangeCode code = unusedCode();
            code.setStatus("EXCHANGED");
            given(codeGenerator.validate(CODE)).willReturn(true);
            given(exchangeCodeMapper.selectOne(any())).willReturn(code);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.exchange(USER_ID, CODE));
            assertEquals("EXCHANGE_CODE_ALREADY_USED", ex.getCode());
        }

        @Test
        @DisplayName("BitMap返回true（已兑换）抛出异常")
        void shouldThrowWhenBitMapIndicatesAlreadyExchanged() {
            given(codeGenerator.validate(CODE)).willReturn(true);
            given(exchangeCodeMapper.selectOne(any())).willReturn(unusedCode());
            given(valueOperations.setBit(anyString(), anyLong(), eq(true))).willReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.exchange(USER_ID, CODE));
            assertEquals("EXCHANGE_CODE_ALREADY_USED", ex.getCode());
        }

        @Test
        @DisplayName("DB CAS返回0（不一致）清除BitMap并抛出异常")
        void shouldClearBitMapWhenDbCasReturnsZero() {
            given(codeGenerator.validate(CODE)).willReturn(true);
            given(exchangeCodeMapper.selectOne(any())).willReturn(unusedCode());
            given(valueOperations.setBit(anyString(), anyLong(), eq(true))).willReturn(false);
            given(redisTemplate.getExpire(anyString())).willReturn(3600L);
            given(exchangeCodeMapper.markExchangedIfUnused(eq(CODE), eq(USER_ID), any(LocalDateTime.class)))
                    .willReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.exchange(USER_ID, CODE));
            assertEquals("EXCHANGE_CODE_ALREADY_USED", ex.getCode());
            then(valueOperations).should().setBit(anyString(), anyLong(), eq(false));
        }

        @Test
        @DisplayName("claimCoupon失败时触发补偿回滚")
        void shouldRollbackWhenClaimCouponFails() {
            given(codeGenerator.validate(CODE)).willReturn(true);
            given(exchangeCodeMapper.selectOne(any())).willReturn(unusedCode());
            given(valueOperations.setBit(anyString(), anyLong(), eq(true))).willReturn(false);
            given(redisTemplate.getExpire(anyString())).willReturn(3600L);
            given(exchangeCodeMapper.markExchangedIfUnused(eq(CODE), eq(USER_ID), any(LocalDateTime.class)))
                    .willReturn(1);
            given(couponService.claimCoupon(USER_ID, TEMPLATE_ID))
                    .willThrow(new BusinessException("STOCK_INSUFFICIENT", "优惠券已领完"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.exchange(USER_ID, CODE));
            assertEquals("STOCK_INSUFFICIENT", ex.getCode());
            // 验证回滚：DB 状态恢复 + BitMap 清除
            then(exchangeCodeMapper).should().rollbackExchanged(CODE);
            then(valueOperations).should().setBit(anyString(), anyLong(), eq(false));
        }
    }

    @Nested
    @DisplayName("getByCode 查询详情")
    class GetByCodeTests {

        @Test
        @DisplayName("成功返回兑换码详情")
        void shouldReturnCodeDetails() {
            given(exchangeCodeMapper.selectOne(any())).willReturn(unusedCode());

            var vo = exchangeCodeService.getByCode(CODE);

            assertEquals(CODE, vo.code());
            assertEquals(TEMPLATE_ID, vo.templateId());
            assertEquals("UNUSED", vo.status());
        }

        @Test
        @DisplayName("兑换码不存在抛出异常")
        void shouldThrowWhenCodeNotFound() {
            given(exchangeCodeMapper.selectOne(any())).willReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.getByCode(CODE));
            assertEquals("EXCHANGE_CODE_NOT_FOUND", ex.getCode());
        }
    }

    @Nested
    @DisplayName("disable 作废兑换码")
    class DisableTests {

        @Test
        @DisplayName("成功作废未兑换的码")
        void shouldDisableUnusedCode() {
            given(exchangeCodeMapper.disableIfUnused(CODE)).willReturn(1);

            exchangeCodeService.disable(CODE);

            then(exchangeCodeMapper).should().disableIfUnused(CODE);
        }

        @Test
        @DisplayName("已兑换的码不允许作废")
        void shouldThrowWhenCodeAlreadyExchanged() {
            ExchangeCode code = unusedCode();
            code.setStatus("EXCHANGED");
            given(exchangeCodeMapper.disableIfUnused(CODE)).willReturn(0);
            given(exchangeCodeMapper.selectOne(any())).willReturn(code);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.disable(CODE));
            assertEquals("EXCHANGE_CODE_STATUS_ERROR", ex.getCode());
        }

        @Test
        @DisplayName("不存在的码作废抛出异常")
        void shouldThrowWhenCodeNotFound() {
            given(exchangeCodeMapper.disableIfUnused(CODE)).willReturn(0);
            given(exchangeCodeMapper.selectOne(any())).willReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exchangeCodeService.disable(CODE));
            assertEquals("EXCHANGE_CODE_NOT_FOUND", ex.getCode());
        }
    }
}
