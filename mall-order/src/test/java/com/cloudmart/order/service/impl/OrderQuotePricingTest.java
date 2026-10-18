package com.cloudmart.order.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.async.compensation.CompensationTaskService;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.converter.OrderConverter;
import com.cloudmart.order.dto.DiscountLine;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.dto.SkuBatchItemDTO;
import com.cloudmart.order.dto.TieredMatchResult;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.entity.OrderQuote;
import com.cloudmart.order.entity.OrderQuoteItem;
import com.cloudmart.order.feign.CartFeignClient;
import com.cloudmart.order.feign.CouponFeignClient;
import com.cloudmart.order.feign.InventoryFeignClient;
import com.cloudmart.order.feign.MarketingFeignClient;
import com.cloudmart.order.feign.ProductFeignClient;
import com.cloudmart.order.feign.RefundFeignClient;
import com.cloudmart.order.feign.RiskFeignClient;
import com.cloudmart.order.feign.SeckillFeignClient;
import com.cloudmart.order.feign.WmsShippingFeignClient;
import com.cloudmart.order.mq.OrderEventProducer;
import com.cloudmart.order.repository.OrderItemMapper;
import com.cloudmart.order.repository.OrderMapper;
import com.cloudmart.order.repository.OrderQuoteItemMapper;
import com.cloudmart.order.repository.OrderQuoteMapper;
import com.cloudmart.order.service.AfterSaleCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T01：报价快照是订单金额的唯一来源。
 *
 * <p>回归“报价 80、建单 100”的缺陷：下单只允许使用已复核的报价快照，
 * 不允许在建单路径二次取价或只算券。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("T01 报价→建单金额一致性")
class OrderQuotePricingTest {

    private static final Long USER_ID = 1L;
    private static final Long QUOTE_ID = 9001L;
    private static final Long ORDER_ID = 555L;
    private static final Long SKU_ID = 10L;

    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderConverter orderConverter;
    @Mock private InventoryFeignClient inventoryFeignClient;
    @Mock private CartFeignClient cartFeignClient;
    @Mock private CouponFeignClient couponFeignClient;
    @Mock private RefundFeignClient refundFeignClient;
    @Mock private ProductFeignClient productFeignClient;
    @Mock private RiskFeignClient riskFeignClient;
    @Mock private WmsShippingFeignClient wmsShippingFeignClient;
    @Mock private SeckillFeignClient seckillFeignClient;
    @Mock private MarketingFeignClient marketingFeignClient;
    @Mock private AfterSaleCaseService afterSaleCaseService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private OrderEventProducer orderEventProducer;
    @Mock private OutboxService outboxService;
    @Mock private CompensationTaskService compensationTaskService;
    @Mock private OrderQuoteMapper orderQuoteMapper;
    @Mock private OrderQuoteItemMapper orderQuoteItemMapper;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(riskFeignClient.check(any())).thenReturn(ApiResponse.ok(Map.of("result", "PASS")));
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(orderQuoteMapper.consume(anyLong(), anyLong())).thenReturn(1);
        when(inventoryFeignClient.deductStock(any())).thenReturn(ApiResponse.ok(true));
        when(couponFeignClient.useCoupon(any())).thenReturn(ApiResponse.ok(null));
        when(cartFeignClient.clearCheckedBySkus(anyLong(), any())).thenReturn(ApiResponse.ok(null));
        when(orderItemMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            inv.getArgument(0, Order.class).setId(ORDER_ID);
            return 1;
        });
        when(orderConverter.toDTO(any(Order.class), any())).thenAnswer(inv -> dtoOf(inv.getArgument(0)));

        orderService = new OrderServiceImpl(
                orderMapper, orderItemMapper, orderConverter,
                inventoryFeignClient, cartFeignClient, couponFeignClient, refundFeignClient,
                new OrderCouponPolicy(couponFeignClient),
                productFeignClient, riskFeignClient, wmsShippingFeignClient, seckillFeignClient,
                marketingFeignClient, afterSaleCaseService,
                redisTemplate, orderEventProducer, outboxService, compensationTaskService,
                new ObjectMapper(), orderQuoteMapper, orderQuoteItemMapper,
                new QuoteDiscountCalculator(marketingFeignClient,
                        new OrderCouponPolicy(couponFeignClient), new ObjectMapper()));
    }

    @Test
    @DisplayName("商品 100 + 满减 20：订单落库 80，优惠行与报价完全一致")
    void tieredOnly_orderUsesQuoteSnapshot() {
        stubSku(100);
        stubTiered(20);
        OrderQuote quote = quote(null, "100.00", "20.00", "80.00",
                linesJson(DiscountLine.tiered(tieredResult(20))));

        orderService.createOrderFromQuote(USER_ID, QUOTE_ID, 1, "idem-tiered",
                "张三", "13800138000", "北京市");

        Order saved = capturedOrder();
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(saved.getPayAmount()).isEqualByComparingTo("80.00");
        assertThat(saved.getDiscountLines()).isEqualTo(quote.getDiscountLines());
    }

    @Test
    @DisplayName("商品 100 + 满减 20 + 立减券 10：订单落库 70，券被占用")
    void tieredAndCoupon_orderUsesQuoteSnapshot() {
        stubSku(100);
        stubTiered(20);
        when(couponFeignClient.getCouponById(9L)).thenReturn(ApiResponse.ok(coupon(9L, "10.00")));
        OrderQuote quote = quote(9L, "100.00", "30.00", "70.00",
                linesJson(DiscountLine.tiered(tieredResult(20)),
                        DiscountLine.coupon(9L, "立减10", new BigDecimal("10.00"))));

        orderService.createOrderFromQuote(USER_ID, QUOTE_ID, 1, "idem-coupon",
                "张三", "13800138000", "北京市");

        Order saved = capturedOrder();
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("30.00");
        assertThat(saved.getPayAmount()).isEqualByComparingTo("70.00");
        assertThat(quote.getPayAmount()).isEqualByComparingTo(saved.getPayAmount());
        verify(couponFeignClient).useCoupon(any());
    }

    @Test
    @DisplayName("满减规则在建单前变化：返回 QUOTE_STALE，不静默涨价")
    void tieredRuleChanged_throwsQuoteStale() {
        stubSku(100);
        stubTiered(0);
        quote(null, "100.00", "20.00", "80.00", linesJson());

        assertThatThrownBy(() -> orderService.createOrderFromQuote(
                USER_ID, QUOTE_ID, 1, "idem-stale", "张三", "13800138000", "北京市"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重新报价");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("商品价格在建单前变化：返回 QUOTE_STALE，不按新价扣款")
    void skuPriceChanged_throwsQuoteStale() {
        stubSku(120);
        stubTiered(20);
        quote(null, "100.00", "20.00", "80.00", linesJson());

        assertThatThrownBy(() -> orderService.createOrderFromQuote(
                USER_ID, QUOTE_ID, 1, "idem-price", "张三", "13800138000", "北京市"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("价格已变化");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    // ---------- 夹具 ----------

    private void stubSku(int price) {
        when(productFeignClient.getSkusBatch(any())).thenReturn(ApiResponse.ok(List.of(
                new SkuBatchItemDTO(SKU_ID, 77L, "测试商品", "/img/a.png",
                        new BigDecimal(price), 1, "红色"))));
        OrderQuoteItem item = new OrderQuoteItem();
        item.setProductId(77L);
        item.setSkuId(SKU_ID);
        item.setProductName("测试商品");
        item.setPrice(new BigDecimal("100.00"));
        item.setQuantity(1);
        item.setSubtotal(new BigDecimal("100.00"));
        when(orderQuoteItemMapper.selectList(any())).thenReturn(List.of(item));
    }

    private void stubTiered(int discount) {
        when(marketingFeignClient.matchTiered(any())).thenReturn(ApiResponse.ok(tieredResult(discount)));
    }

    private TieredMatchResult tieredResult(int discount) {
        return new TieredMatchResult(11L, "满100减" + discount, 22L,
                new BigDecimal("100.00"), new BigDecimal(discount), discount > 0);
    }

    private OrderQuote quote(Long couponId, String total, String discount, String pay, String linesJson) {
        OrderQuote quote = new OrderQuote();
        quote.setId(QUOTE_ID);
        quote.setUserId(USER_ID);
        quote.setVersion(1);
        quote.setStatus("ACTIVE");
        quote.setCouponId(couponId);
        quote.setTotalAmount(new BigDecimal(total));
        quote.setDiscountAmount(new BigDecimal(discount));
        quote.setPayAmount(new BigDecimal(pay));
        quote.setDiscountLines(linesJson);
        quote.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        when(orderQuoteMapper.selectById(QUOTE_ID)).thenReturn(quote);
        return quote;
    }

    private CouponFeignClient.UserCouponDTO coupon(Long couponId, String discountAmount) {
        return new CouponFeignClient.UserCouponDTO(couponId, USER_ID, 33L, "UNUSED", null,
                null, null, LocalDateTime.now().plusDays(7).toString(),
                "立减" + discountAmount, "AMOUNT_OFF",
                BigDecimal.ZERO, new BigDecimal(discountAmount), null);
    }

    private String linesJson(DiscountLine... lines) {
        return new ObjectMapper().writeValueAsString(List.of(lines));
    }

    private Order capturedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        return captor.getValue();
    }

    private OrderDTO dtoOf(Order order) {
        return new OrderDTO(order.getId(), order.getOrderNo(), order.getTotalAmount(),
                order.getPayAmount(), order.getDiscountAmount(), order.getCouponId(),
                order.getStatus(), order.getReceiverName(), order.getReceiverPhone(),
                order.getReceiverAddress(), null, null, null, null, List.of(),
                null, null, "NONE", BigDecimal.ZERO);
    }
}
