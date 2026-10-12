package com.cloudmart.order.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.entity.OrderQuote;
import com.cloudmart.order.feign.ProductFeignClient;
import com.cloudmart.order.repository.OrderQuoteItemMapper;
import com.cloudmart.order.repository.OrderQuoteMapper;
import com.cloudmart.order.service.QuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TRADE-01：服务端报价——价格/商品信息一律取自商品服务权威值（客户端金额字段
 * 根本不存在于入参）；SKU 不存在/下架拒绝；重复 SKU 合并；数量与行数边界。
 */
@DisplayName("QuoteServiceImpl 服务端报价")
class QuoteServiceImplTest {

    private static final Long USER_ID = 42L;

    private QuoteServiceImpl quoteService;
    private ProductFeignClient productFeignClient;
    private OrderQuoteMapper quoteMapper;
    private OrderQuoteItemMapper quoteItemMapper;
    private com.cloudmart.order.feign.MarketingFeignClient marketingFeignClient;
    private com.cloudmart.order.feign.CouponFeignClient couponFeignClient;

    @BeforeEach
    void setUp() {
        productFeignClient = mock(ProductFeignClient.class);
        quoteMapper = mock(OrderQuoteMapper.class);
        quoteItemMapper = mock(OrderQuoteItemMapper.class);
        marketingFeignClient = mock(com.cloudmart.order.feign.MarketingFeignClient.class);
        couponFeignClient = org.mockito.Mockito.mock(com.cloudmart.order.feign.CouponFeignClient.class);
        // R12：默认无满减命中（R12 用例按场景覆盖）
        org.mockito.Mockito.lenient().when(marketingFeignClient.matchTiered(any())).thenReturn(
                ApiResponse.ok(new com.cloudmart.order.dto.TieredMatchResult(
                        null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, false)));
        quoteService = new QuoteServiceImpl(productFeignClient, quoteMapper, quoteItemMapper,
                new QuoteDiscountCalculator(marketingFeignClient, new OrderCouponPolicy(couponFeignClient),
                        new tools.jackson.databind.ObjectMapper()), 300L);
    }

    /** R24：Feign 响应已强类型化——mock 直接构造 DTO，字符串形态由 Jackson 反序列化层负责 */
    private com.cloudmart.order.dto.SkuBatchItemDTO sku(long skuId, long productId, String price, int status) {
        return new com.cloudmart.order.dto.SkuBatchItemDTO(
                skuId, productId, "商品" + skuId, "http://img/" + skuId + ".png",
                new BigDecimal(price), status, "颜色:红");
    }

    @Test
    @DisplayName("价格取服务端权威值：总价 = 权威单价 × 数量（客户端无金额入参）")
    void createQuote_priceFromServer() {
        when(productFeignClient.getSkusBatch(List.of(100L)))
                .thenReturn(ApiResponse.ok(List.of(sku(100L, 1L, "29.90", 1))));

        OrderQuote quote = quoteService.createQuote(USER_ID,
                List.of(new QuoteService.QuoteItemInput(100L, 3)), null);

        // 29.90 × 3 = 89.70，与客户端声明无关（入参根本没有金额字段）
        assertThat(quote.getTotalAmount()).isEqualByComparingTo(new BigDecimal("89.70"));
        assertThat(quote.getPayAmount()).isEqualByComparingTo(new BigDecimal("89.70"));
        assertThat(quote.getStatus()).isEqualTo("ACTIVE");
        assertThat(quote.getUserId()).isEqualTo(USER_ID);
        assertThat(quote.getExpiresAt()).isAfter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(240));

        ArgumentCaptor<com.cloudmart.order.entity.OrderQuoteItem> itemCaptor =
                ArgumentCaptor.forClass(com.cloudmart.order.entity.OrderQuoteItem.class);
        verify(quoteItemMapper).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getPrice()).isEqualByComparingTo(new BigDecimal("29.90"));
        assertThat(itemCaptor.getValue().getSubtotal()).isEqualByComparingTo(new BigDecimal("89.70"));
        assertThat(itemCaptor.getValue().getProductName()).isEqualTo("商品100");
    }

    @Test
    @DisplayName("重复 SKU 合并（2+3=5 行内一次计算），数量超 999 拒绝")
    void createQuote_mergeDuplicateSku_andQuantityBounds() {
        when(productFeignClient.getSkusBatch(List.of(100L)))
                .thenReturn(ApiResponse.ok(List.of(sku(100L, 1L, "10.00", 1))));

        OrderQuote quote = quoteService.createQuote(USER_ID,
                List.of(new QuoteService.QuoteItemInput(100L, 2),
                        new QuoteService.QuoteItemInput(100L, 3)), null);

        assertThat(quote.getTotalAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        verify(productFeignClient).getSkusBatch(List.of(100L));

        assertThatThrownBy(() -> quoteService.createQuote(USER_ID,
                List.of(new QuoteService.QuoteItemInput(100L, 1000)), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_QUANTITY");
    }

    @Test
    @DisplayName("SKU 不存在 / 已下架 / 商品服务不可用 一律拒绝")
    void createQuote_invalidSkus_rejected() {
        // 商品服务 fail-closed
        when(productFeignClient.getSkusBatch(anyList())).thenReturn(null);
        assertThatThrownBy(() -> quoteService.createQuote(USER_ID,
                List.of(new QuoteService.QuoteItemInput(100L, 1)), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PRODUCT_SERVICE_UNAVAILABLE");

        // SKU 不存在
        when(productFeignClient.getSkusBatch(anyList())).thenReturn(ApiResponse.ok(List.of()));
        assertThatThrownBy(() -> quoteService.createQuote(USER_ID,
                List.of(new QuoteService.QuoteItemInput(404L, 1)), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SKU_NOT_FOUND");

        // 已下架
        when(productFeignClient.getSkusBatch(anyList()))
                .thenReturn(ApiResponse.ok(List.of(sku(100L, 1L, "10.00", 0))));
        assertThatThrownBy(() -> quoteService.createQuote(USER_ID,
                List.of(new QuoteService.QuoteItemInput(100L, 1)), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SKU_OFF_SALE");

        verify(quoteMapper, times(0)).insert(any(OrderQuote.class));
    }

    @Test
    @DisplayName("空商品列表与超 100 行拒绝")
    void createQuote_bounds_rejected() {
        assertThatThrownBy(() -> quoteService.createQuote(USER_ID, List.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "QUOTE_EMPTY");

        List<QuoteService.QuoteItemInput> tooMany = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(i -> new QuoteService.QuoteItemInput((long) i, 1))
                .toList();
        assertThatThrownBy(() -> quoteService.createQuote(USER_ID, tooMany, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "QUOTE_TOO_MANY_ITEMS");
    }

    @Test
    @DisplayName("查询：他人报价按不存在处理（不泄露存在性）")
    void getQuote_ofOtherUser_notFound() {
        OrderQuote others = new OrderQuote();
        others.setId(9L);
        others.setUserId(999L);
        when(quoteMapper.selectById(9L)).thenReturn(others);

        assertThatThrownBy(() -> quoteService.getQuote(USER_ID, 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "QUOTE_NOT_FOUND");
        verify(quoteItemMapper, never()).selectList(any());
    }

    @Nested
    @DisplayName("R12 满减接入报价（商品金额→满减→券→应付）")
    class R12TieredTests {

        @Test
        @DisplayName("命中满减：应付=商品金额-满减，discountLines 落活动/规则快照")
        void createQuote_tieredMatched() {
            when(productFeignClient.getSkusBatch(List.of(100L)))
                    .thenReturn(ApiResponse.ok(List.of(sku(100L, 1L, "100.00", 1))));
            when(marketingFeignClient.matchTiered(any())).thenReturn(ApiResponse.ok(
                    new com.cloudmart.order.dto.TieredMatchResult(
                            7L, "满100减20", 701L, new BigDecimal("100.00"),
                            new BigDecimal("20.00"), true)));

            OrderQuote quote = quoteService.createQuote(USER_ID,
                    List.of(new QuoteService.QuoteItemInput(100L, 1)), null);

            assertThat(quote.getTotalAmount()).isEqualByComparingTo("100.00");
            assertThat(quote.getDiscountAmount()).isEqualByComparingTo("20.00");
            assertThat(quote.getPayAmount()).isEqualByComparingTo("80.00");
            assertThat(quote.getDiscountLines()).contains("\"promotionId\":7")
                    .contains("\"matchedRuleId\":701")
                    .contains("满100减20");
        }

        @Test
        @DisplayName("满减与券叠加：券按满减后剩余金额减免，不重复减免满减部分")
        void createQuote_tieredAndCouponStacked() {
            when(productFeignClient.getSkusBatch(List.of(100L)))
                    .thenReturn(ApiResponse.ok(List.of(sku(100L, 1L, "100.00", 1))));
            when(marketingFeignClient.matchTiered(any())).thenReturn(ApiResponse.ok(
                    new com.cloudmart.order.dto.TieredMatchResult(
                            7L, "满100减20", 701L, new BigDecimal("100.00"),
                            new BigDecimal("20.00"), true)));
            // 直减 30 券（无门槛）——对满减后 80 基数减免 30
            com.cloudmart.order.feign.CouponFeignClient.UserCouponDTO coupon =
                    new com.cloudmart.order.feign.CouponFeignClient.UserCouponDTO(
                            9001L, USER_ID, 5L, "UNUSED", null, null, null, null,
                            "满80减30券", "AMOUNT_OFF", null, new BigDecimal("30.00"), null);
            when(couponFeignClient.getCouponById(9001L)).thenReturn(ApiResponse.ok(coupon));

            OrderQuote quote = quoteService.createQuote(USER_ID,
                    List.of(new QuoteService.QuoteItemInput(100L, 1)), 9001L);

            // 100 - 20（满减）- 30（券按剩余 80 基数直减）= 50
            assertThat(quote.getPayAmount()).isEqualByComparingTo("50.00");
            assertThat(quote.getDiscountAmount()).isEqualByComparingTo("50.00");
            assertThat(quote.getDiscountLines()).contains("TIERED").contains("COUPON");
        }

        @Test
        @DisplayName("营销服务不可达：fail-closed 拒绝报价（不漏算优惠导致少收款）")
        void createQuote_marketingDown_rejected() {
            when(productFeignClient.getSkusBatch(List.of(100L)))
                    .thenReturn(ApiResponse.ok(List.of(sku(100L, 1L, "100.00", 1))));
            when(marketingFeignClient.matchTiered(any())).thenReturn(null);

            assertThatThrownBy(() -> quoteService.createQuote(USER_ID,
                    List.of(new QuoteService.QuoteItemInput(100L, 1)), null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "TIERED_PROMOTION_UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("T18 合并数量复检与 UTC 有效期")
    class T18MergeAndExpiryTests {

        @Test
        @DisplayName("同 SKU 多行合并后超过单行上限 → INVALID_QUANTITY（聚合复检）")
        void createQuote_mergedTotalOverLimit_rejected() {
            var line1 = new QuoteService.QuoteItemInput(10L, 999);
            var line2 = new QuoteService.QuoteItemInput(10L, 999);

            assertThatThrownBy(() -> quoteService.createQuote(1L, List.of(line1, line2), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("INVALID_QUANTITY");
            verify(productFeignClient, never()).getSkusBatch(any());
        }

        @Test
        @DisplayName("报价有效期以 UTC 基准生成（与下单侧 UTC 比较一致）")
        void createQuote_expiryUtc() {
            when(productFeignClient.getSkusBatch(any())).thenReturn(ApiResponse.ok(List.of(
                    sku(10L, 100L, "10.00", 1))));
            var quote = quoteService.createQuote(1L, List.of(new QuoteService.QuoteItemInput(10L, 2)), null);

            // UTC 基准：服务器默认时区为 UTC+8 时，本地 now() 会比 UTC now() 大 8 小时——
            // 旧实现用本地 now 生成有效期，UTC 比较侧看到的有效期偏短 8 小时
            assertThat(quote.getExpiresAt())
                    .isAfter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(200));
        }
    }
}
