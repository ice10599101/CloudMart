package com.cloudmart.marketing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.*;
import com.cloudmart.marketing.entity.TieredPromotion;
import com.cloudmart.marketing.entity.TieredPromotionRule;
import com.cloudmart.marketing.feign.ProductSkuFeignClient;
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

    @Mock
    private com.cloudmart.marketing.feign.ProductSkuFeignClient productSkuFeignClient;

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

    @Nested
    @DisplayName("R12 购物篮自动匹配 matchBest / 发布前预览")
    class R12MatchTests {

        private TieredPromotion activePromo(Long id, String name) {
            TieredPromotion p = new TieredPromotion();
            p.setId(id);
            p.setName(name);
            p.setStatus("ENABLED");
            p.setStartTime(NOW.minusDays(1));
            p.setEndTime(NOW.plusDays(7));
            return p;
        }

        private TieredPromotionRule rule(Long promotionId, Long ruleId, String min, String discount) {
            TieredPromotionRule r = new TieredPromotionRule();
            r.setId(ruleId);
            r.setPromotionId(promotionId);
            r.setMinAmount(new BigDecimal(min));
            r.setDiscountAmount(new BigDecimal(discount));
            return r;
        }

        @Test
        @DisplayName("未达门槛：matched=false 且金额为 0")
        void matchBest_belowThreshold_none() {
            when(promotionMapper.selectList(any())).thenReturn(List.of(activePromo(1L, "满100减20")));
            when(ruleMapper.selectList(any())).thenReturn(List.of());

            TieredMatchResult result = service.matchBest(new TieredMatchRequest(new BigDecimal("99")));

            assertThat(result.matched()).isFalse();
            assertThat(result.discountAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("单活动命中：返回活动/规则/门槛/金额")
        void matchBest_singleMatched() {
            when(promotionMapper.selectList(any())).thenReturn(List.of(activePromo(1L, "满100减20")));
            when(ruleMapper.selectList(any())).thenReturn(List.of(rule(1L, 101L, "100", "20")));

            TieredMatchResult result = service.matchBest(new TieredMatchRequest(new BigDecimal("100")));

            assertThat(result.matched()).isTrue();
            assertThat(result.promotionId()).isEqualTo(1L);
            assertThat(result.matchedRuleId()).isEqualTo(101L);
            assertThat(result.minAmount()).isEqualByComparingTo("100");
            assertThat(result.discountAmount()).isEqualByComparingTo("20");
        }

        @Test
        @DisplayName("两活动重叠：优惠金额更大者胜（确定性，非随机）")
        void matchBest_twoOverlapping_biggestWins() {
            when(promotionMapper.selectList(any())).thenReturn(List.of(
                    activePromo(1L, "满100减20"), activePromo(2L, "满100减30")));
            when(ruleMapper.selectList(any())).thenReturn(List.of(
                    rule(1L, 101L, "100", "20"), rule(2L, 201L, "100", "30")));

            TieredMatchResult result = service.matchBest(new TieredMatchRequest(new BigDecimal("100")));

            assertThat(result.promotionId()).isEqualTo(2L);
            assertThat(result.discountAmount()).isEqualByComparingTo("30");
        }

        @Test
        @DisplayName("同活动多档可达：只取最高阶梯（规则顺序无关）")
        void matchBest_samePromotionMultipleTiers_picksHighest() {
            when(promotionMapper.selectList(any())).thenReturn(List.of(activePromo(1L, "阶梯满减")));
            when(ruleMapper.selectList(any())).thenReturn(java.util.List.of(
                    rule(1L, 102L, "200", "50"), rule(1L, 101L, "100", "20")));

            TieredMatchResult result = service.matchBest(new TieredMatchRequest(new BigDecimal("300")));

            assertThat(result.matchedRuleId()).isEqualTo(102L);
            assertThat(result.discountAmount()).isEqualByComparingTo("50");
        }

        @Test
        @DisplayName("DISABLED/时间窗外活动不参与匹配（DB 过滤后无生效活动→不查规则）")
        void matchBest_disabledAndExpired_excluded() {
            // SQL 已按 status/时间窗过滤——无生效活动时返回空集
            when(promotionMapper.selectList(any())).thenReturn(List.of());

            TieredMatchResult result = service.matchBest(new TieredMatchRequest(new BigDecimal("500")));

            assertThat(result.matched()).isFalse();
            verify(ruleMapper, times(0)).selectList(any());
        }

        @Test
        @DisplayName("发布前预览：DISABLED 活动忽略状态/时间窗同样试算命中结果")
        void previewDiscount_ignoresStatusAndWindow() {
            TieredPromotion disabled = activePromo(5L, "待发布");
            disabled.setStatus("DISABLED");
            disabled.setStartTime(NOW.plusDays(30));
            when(promotionMapper.selectById(5L)).thenReturn(disabled);
            when(ruleMapper.selectList(any())).thenReturn(List.of(rule(5L, 501L, "200", "50")));

            CalculateDiscountResult result = service.previewDiscount(5L, new BigDecimal("200"));

            assertThat(result.matched()).isTrue();
            assertThat(result.discountAmount()).isEqualByComparingTo("50");
        }
    }

    @Nested
    @DisplayName("T10 preview 试算")
    class PreviewTests {

        @Test
        @DisplayName("DISABLED 活动：命中规则但明确告知用户侧不可用")
        void preview_disabled_reportsBlockingReason() {
            TieredPromotion promotion = buildPromotion(5L, "DISABLED");
            when(promotionMapper.selectById(5L)).thenReturn(promotion);
            when(ruleMapper.selectList(any())).thenReturn(List.of(
                    buildRule(501L, 5L, new BigDecimal("100"), new BigDecimal("20")),
                    buildRule(502L, 5L, new BigDecimal("200"), new BigDecimal("50"))));

            TieredPreviewResult result = service.preview(5L,
                    new TieredPreviewRequest(null, new BigDecimal("150"), null));

            assertThat(result.matched()).isTrue();
            assertThat(result.userVisible()).isFalse();
            assertThat(result.discountAmount()).isEqualByComparingTo("20");
            assertThat(result.reasons()).anyMatch(reason -> reason.contains("达到门槛"));
            assertThat(result.blockingReasons()).anyMatch(reason -> reason.contains("DISABLED"));
        }

        @Test
        @DisplayName("未达最低门槛：明确给出差额说明且不命中")
        void preview_belowLowestThreshold_explainsNoMatch() {
            TieredPromotion promotion = buildPromotion(5L, "ENABLED");
            when(promotionMapper.selectById(5L)).thenReturn(promotion);
            when(ruleMapper.selectList(any())).thenReturn(List.of(
                    buildRule(501L, 5L, new BigDecimal("100"), new BigDecimal("20"))));

            TieredPreviewResult result = service.preview(5L,
                    new TieredPreviewRequest(null, new BigDecimal("60"), null));

            assertThat(result.matched()).isFalse();
            assertThat(result.discountAmount()).isEqualByComparingTo("0");
            assertThat(result.reasons()).anyMatch(reason -> reason.contains("未达最低门槛"));
        }

        @Test
        @DisplayName("SKU+数量：按商品权威价换算购物篮金额（合并同 SKU）")
        void preview_itemsUseAuthorityPrice() {
            TieredPromotion promotion = buildPromotion(5L, "ENABLED");
            when(promotionMapper.selectById(5L)).thenReturn(promotion);
            when(ruleMapper.selectList(any())).thenReturn(List.of(
                    buildRule(501L, 5L, new BigDecimal("100"), new BigDecimal("20"))));
            when(productSkuFeignClient.getSkusBatch(any())).thenReturn(ApiResponse.ok(List.of(
                    new ProductSkuFeignClient.SkuBatchItem(10L, 1L, "商品", null,
                            new BigDecimal("50.00"), 1))));

            TieredPreviewResult result = service.preview(5L, new TieredPreviewRequest(
                    List.of(new TieredPreviewRequest.PreviewItem(10L, 2),
                            new TieredPreviewRequest.PreviewItem(10L, 1)),
                    null, null));

            assertThat(result.orderAmount()).isEqualByComparingTo("150.00");
            assertThat(result.matched()).isTrue();
        }

        @Test
        @DisplayName("SKU 不存在 → SKU_NOT_FOUND")
        void preview_missingSku_rejected() {
            TieredPromotion promotion = buildPromotion(5L, "ENABLED");
            when(promotionMapper.selectById(5L)).thenReturn(promotion);
            when(productSkuFeignClient.getSkusBatch(any())).thenReturn(ApiResponse.ok(List.of()));

            assertThatThrownBy(() -> service.preview(5L, new TieredPreviewRequest(
                    List.of(new TieredPreviewRequest.PreviewItem(10L, 1)), null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("SKU_NOT_FOUND");
        }

        @Test
        @DisplayName("合并后数量超上限 → INVALID_QUANTITY（不请求商品服务）")
        void preview_mergedQuantityOverLimit_rejected() {
            TieredPromotion promotion = buildPromotion(5L, "ENABLED");
            when(promotionMapper.selectById(5L)).thenReturn(promotion);

            assertThatThrownBy(() -> service.preview(5L, new TieredPreviewRequest(
                    List.of(new TieredPreviewRequest.PreviewItem(10L, 600),
                            new TieredPreviewRequest.PreviewItem(10L, 600)), null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("INVALID_QUANTITY");
            verify(productSkuFeignClient, times(0)).getSkusBatch(any());
        }
    }
}
