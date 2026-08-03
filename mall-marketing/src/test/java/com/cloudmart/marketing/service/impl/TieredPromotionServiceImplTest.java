package com.cloudmart.marketing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.*;
import com.cloudmart.marketing.entity.TieredPromotion;
import com.cloudmart.marketing.entity.TieredPromotionRule;
import com.cloudmart.marketing.repository.TieredPromotionMapper;
import com.cloudmart.marketing.repository.TieredPromotionRuleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TieredPromotionServiceImpl 单元测试")
class TieredPromotionServiceImplTest {

    @Mock
    private TieredPromotionMapper promotionMapper;

    @Mock
    private TieredPromotionRuleMapper ruleMapper;

    @Mock
    private MarketingConverter converter;

    @InjectMocks
    private TieredPromotionServiceImpl service;

    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final LocalDateTime START = NOW.plusDays(1);
    private static final LocalDateTime END = NOW.plusDays(7);

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), TieredPromotion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), TieredPromotionRule.class);
    }

    private TieredPromotion buildPromotion(Long id, String status) {
        TieredPromotion p = new TieredPromotion();
        p.setId(id);
        p.setName("满减活动");
        p.setDescription("满100减20");
        p.setStartTime(START);
        p.setEndTime(END);
        p.setStatus(status);
        p.setCreatedAt(NOW);
        return p;
    }

    private TieredPromotionRule buildRule(Long id, Long promotionId, BigDecimal minAmount, BigDecimal discount) {
        TieredPromotionRule rule = new TieredPromotionRule();
        rule.setId(id);
        rule.setPromotionId(promotionId);
        rule.setMinAmount(minAmount);
        rule.setDiscountAmount(discount);
        rule.setCreatedAt(NOW);
        return rule;
    }

    private TieredPromotionDTO buildPromotionDTO(TieredPromotion p, List<TieredRuleDTO> rules) {
        return new TieredPromotionDTO(
                p.getId(), p.getName(), p.getDescription(),
                p.getStartTime(), p.getEndTime(), p.getStatus(), p.getCreatedAt(), rules
        );
    }

    private void stubGetPromotion(TieredPromotion p, List<TieredPromotionRule> rules) {
        TieredPromotionDTO baseDto = new TieredPromotionDTO(
                p.getId(), p.getName(), p.getDescription(),
                p.getStartTime(), p.getEndTime(), p.getStatus(), p.getCreatedAt(), null
        );
        List<TieredRuleDTO> ruleDtos = rules.stream()
                .map(r -> new TieredRuleDTO(r.getId(), r.getMinAmount(), r.getDiscountAmount()))
                .toList();

        when(promotionMapper.selectById(p.getId())).thenReturn(p);
        when(ruleMapper.selectList(any())).thenReturn(rules);
        when(converter.toDTO(p)).thenReturn(baseDto);
        when(converter.toRuleDTOList(rules)).thenReturn(ruleDtos);
    }

    @Nested
    @DisplayName("createPromotion 方法")
    class CreatePromotionTest {

        @Test
        @DisplayName("正常创建满减活动 - 成功")
        void shouldCreatePromotionSuccessfully() {
            List<TieredRuleRequest> ruleRequests = List.of(
                    new TieredRuleRequest(new BigDecimal("100"), new BigDecimal("10")),
                    new TieredRuleRequest(new BigDecimal("200"), new BigDecimal("30"))
            );
            CreateTieredPromotionRequest request = new CreateTieredPromotionRequest(
                    "满减", "描述", START, END, ruleRequests
            );

            TieredPromotion entity = buildPromotion(null, "DISABLED");
            entity.setId(1L);
            TieredPromotionRule rule1 = buildRule(null, 1L, new BigDecimal("100"), new BigDecimal("10"));
            TieredPromotionRule rule2 = buildRule(null, 1L, new BigDecimal("200"), new BigDecimal("30"));

            when(converter.toEntity(request)).thenReturn(entity);
            when(converter.toEntity(1L, ruleRequests.get(0))).thenReturn(rule1);
            when(converter.toEntity(1L, ruleRequests.get(1))).thenReturn(rule2);

            TieredPromotion savedP = buildPromotion(1L, "DISABLED");
            List<TieredPromotionRule> savedRules = List.of(
                    buildRule(10L, 1L, new BigDecimal("100"), new BigDecimal("10")),
                    buildRule(11L, 1L, new BigDecimal("200"), new BigDecimal("30"))
            );
            stubGetPromotion(savedP, savedRules);

            TieredPromotionDTO result = service.createPromotion(request);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("满减活动");
            verify(promotionMapper).insert(any(TieredPromotion.class));
            verify(ruleMapper, times(2)).insert(any(TieredPromotionRule.class));
        }

        @Test
        @DisplayName("开始时间晚于结束时间 - 抛出异常")
        void shouldThrowWhenStartTimeAfterEndTime() {
            CreateTieredPromotionRequest request = new CreateTieredPromotionRequest(
                    "满减", "描述", END, START,
                    List.of(new TieredRuleRequest(new BigDecimal("100"), new BigDecimal("10")))
            );

            assertThatThrownBy(() -> service.createPromotion(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_TIME_RANGE"));
        }

        @Test
        @DisplayName("规则为空 - 抛出异常")
        void shouldThrowWhenRulesEmpty() {
            CreateTieredPromotionRequest request = new CreateTieredPromotionRequest(
                    "满减", "描述", START, END, List.of()
            );

            assertThatThrownBy(() -> service.createPromotion(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RULES_REQUIRED"));
        }

        @Test
        @DisplayName("规则最低金额未递增 - 抛出异常")
        void shouldThrowWhenRuleMinAmountNotIncreasing() {
            List<TieredRuleRequest> rules = List.of(
                    new TieredRuleRequest(new BigDecimal("200"), new BigDecimal("30")),
                    new TieredRuleRequest(new BigDecimal("100"), new BigDecimal("10"))
            );
            CreateTieredPromotionRequest request = new CreateTieredPromotionRequest(
                    "满减", "描述", START, END, rules
            );

            assertThatThrownBy(() -> service.createPromotion(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_RULE_ORDER"));
        }

        @Test
        @DisplayName("优惠金额大于等于最低消费金额 - 抛出异常")
        void shouldThrowWhenDiscountExceedsMinAmount() {
            List<TieredRuleRequest> rules = List.of(
                    new TieredRuleRequest(new BigDecimal("100"), new BigDecimal("100"))
            );
            CreateTieredPromotionRequest request = new CreateTieredPromotionRequest(
                    "满减", "描述", START, END, rules
            );

            assertThatThrownBy(() -> service.createPromotion(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_DISCOUNT"));
        }
    }

    @Nested
    @DisplayName("enablePromotion 方法")
    class EnablePromotionTest {

        @Test
        @DisplayName("正常启用 - 成功")
        void shouldEnablePromotionSuccessfully() {
            TieredPromotion p = buildPromotion(1L, "DISABLED");
            List<TieredPromotionRule> rules = List.of(
                    buildRule(10L, 1L, new BigDecimal("100"), new BigDecimal("10"))
            );
            stubGetPromotion(p, rules);

            TieredPromotionDTO result = service.enablePromotion(1L);

            assertThat(p.getStatus()).isEqualTo("ENABLED");
            verify(promotionMapper).updateById(p);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("活动不存在 - 抛出异常")
        void shouldThrowWhenPromotionNotFound() {
            when(promotionMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> service.enablePromotion(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PROMOTION_NOT_FOUND"));
        }

        @Test
        @DisplayName("已结束的活动不可启用 - 抛出异常")
        void shouldThrowWhenPromotionEnded() {
            TieredPromotion p = buildPromotion(1L, "ENDED");
            when(promotionMapper.selectById(1L)).thenReturn(p);

            assertThatThrownBy(() -> service.enablePromotion(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PROMOTION_ENDED"));
        }
    }

    @Nested
    @DisplayName("disablePromotion 方法")
    class DisablePromotionTest {

        @Test
        @DisplayName("正常禁用 - 成功")
        void shouldDisablePromotionSuccessfully() {
            TieredPromotion p = buildPromotion(1L, "ENABLED");
            List<TieredPromotionRule> rules = List.of(
                    buildRule(10L, 1L, new BigDecimal("100"), new BigDecimal("10"))
            );
            stubGetPromotion(p, rules);

            TieredPromotionDTO result = service.disablePromotion(1L);

            assertThat(p.getStatus()).isEqualTo("DISABLED");
            verify(promotionMapper).updateById(p);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("活动不存在 - 抛出异常")
        void shouldThrowWhenPromotionNotFound() {
            when(promotionMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> service.disablePromotion(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PROMOTION_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("calculateDiscount 方法")
    class CalculateDiscountTest {

        @Test
        @DisplayName("匹配到最高阶梯规则 - 返回最大优惠")
        void shouldMatchHighestTier() {
            TieredPromotion p = buildPromotion(1L, "ENABLED");
            p.setStartTime(NOW.minusDays(1));
            p.setEndTime(NOW.plusDays(1));

            TieredPromotionRule rule = buildRule(10L, 1L, new BigDecimal("100"), new BigDecimal("20"));

            when(promotionMapper.selectById(1L)).thenReturn(p);
            when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

            CalculateDiscountRequest request = new CalculateDiscountRequest(1L, new BigDecimal("150"));
            CalculateDiscountResult result = service.calculateDiscount(request);

            assertThat(result.matched()).isTrue();
            assertThat(result.matchedRuleId()).isEqualTo(10L);
            assertThat(result.discountAmount()).isEqualByComparingTo(new BigDecimal("20"));
        }

        @Test
        @DisplayName("订单金额为零 - 不匹配")
        void shouldNotMatchWhenAmountZero() {
            CalculateDiscountRequest request = new CalculateDiscountRequest(1L, BigDecimal.ZERO);
            CalculateDiscountResult result = service.calculateDiscount(request);

            assertThat(result.matched()).isFalse();
        }

        @Test
        @DisplayName("活动未启用 - 不匹配")
        void shouldNotMatchWhenPromotionNotEnabled() {
            TieredPromotion p = buildPromotion(1L, "DISABLED");
            when(promotionMapper.selectById(1L)).thenReturn(p);

            CalculateDiscountRequest request = new CalculateDiscountRequest(1L, new BigDecimal("150"));
            CalculateDiscountResult result = service.calculateDiscount(request);

            assertThat(result.matched()).isFalse();
        }

        @Test
        @DisplayName("无匹配规则 - 不匹配")
        void shouldNotMatchWhenNoRulesMatch() {
            TieredPromotion p = buildPromotion(1L, "ENABLED");
            p.setStartTime(NOW.minusDays(1));
            p.setEndTime(NOW.plusDays(1));

            when(promotionMapper.selectById(1L)).thenReturn(p);
            when(ruleMapper.selectList(any())).thenReturn(List.of());

            CalculateDiscountRequest request = new CalculateDiscountRequest(1L, new BigDecimal("50"));
            CalculateDiscountResult result = service.calculateDiscount(request);

            assertThat(result.matched()).isFalse();
        }
    }
}
