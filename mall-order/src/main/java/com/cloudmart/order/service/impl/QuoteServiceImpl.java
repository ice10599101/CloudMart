package com.cloudmart.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.entity.OrderQuote;
import com.cloudmart.order.entity.OrderQuoteItem;
import com.cloudmart.order.feign.ProductFeignClient;
import com.cloudmart.order.repository.OrderQuoteItemMapper;
import com.cloudmart.order.repository.OrderQuoteMapper;
import com.cloudmart.order.service.QuoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端报价实现（TRADE-01）。
 *
 * <p>安全不变量：</p>
 * <ul>
 *   <li>价格、商品名、图片、属性一律取自商品服务权威值——客户端只声明
 *       skuId + quantity，任何金额/商品信息字段被忽略；</li>
 *   <li>SKU 必须存在且 status=1（在售），否则报价行不可用（直接拒绝）；</li>
 *   <li>数量 1-999、单次最多 100 行、重复 SKU 合并——防负数/超大数量与滥用；</li>
 *   <li>报价落库并绑定 owner 与 5 分钟有效期；下单按 (quoteId, CAS ACTIVE→CONSUMED)
 *       消费，一报价只能下一单；过期/已用报价不可下单。</li>
 * </ul>
 */
@Slf4j
@Service
public class QuoteServiceImpl implements QuoteService {

    private static final int MAX_ITEMS = 100;
    private static final int MAX_QUANTITY_PER_LINE = 999;

    private final ProductFeignClient productFeignClient;
    private final OrderQuoteMapper quoteMapper;
    private final OrderQuoteItemMapper quoteItemMapper;
    private final long quoteTtlSeconds;

    public QuoteServiceImpl(ProductFeignClient productFeignClient,
                            OrderQuoteMapper quoteMapper,
                            OrderQuoteItemMapper quoteItemMapper,
                            @Value("${order.quote.ttl-seconds:300}") long quoteTtlSeconds) {
        this.productFeignClient = productFeignClient;
        this.quoteMapper = quoteMapper;
        this.quoteItemMapper = quoteItemMapper;
        this.quoteTtlSeconds = quoteTtlSeconds;
    }

    @Override
    @Transactional
    public OrderQuote createQuote(Long userId, List<QuoteItemInput> items, Long couponId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("QUOTE_EMPTY", "报价商品不能为空");
        }
        if (items.size() > MAX_ITEMS) {
            throw new BusinessException("QUOTE_TOO_MANY_ITEMS", "单次报价最多 " + MAX_ITEMS + " 行");
        }

        // 合并重复 SKU（保持首次出现顺序），数量上限校验
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (QuoteItemInput item : items) {
            if (item == null || item.skuId() == null || item.quantity() == null
                    || item.quantity() < 1 || item.quantity() > MAX_QUANTITY_PER_LINE) {
                throw new BusinessException("INVALID_QUANTITY", "数量必须为 1-" + MAX_QUANTITY_PER_LINE);
            }
            merged.merge(item.skuId(), item.quantity(), Integer::sum);
        }

        // 服务端权威取价（fail-closed：商品服务不可用即拒绝）
        ApiResponse<List<Map<String, Object>>> response = productFeignClient.getSkusBatch(List.copyOf(merged.keySet()));
        if (response == null || !response.success() || response.data() == null) {
            throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务暂不可用，无法生成报价");
        }
        Map<Long, Map<String, Object>> skuMap = new LinkedHashMap<>();
        for (Map<String, Object> sku : response.data()) {
            Object id = sku.get("skuId");
            if (id instanceof Number n) {
                skuMap.put(n.longValue(), sku);
            }
        }

        OrderQuote quote = new OrderQuote();
        quote.setUserId(userId);
        quote.setVersion(1);
        quote.setStatus("ACTIVE");
        quote.setCouponId(couponId);
        quote.setExpiresAt(LocalDateTime.now().plusSeconds(quoteTtlSeconds));

        BigDecimal total = BigDecimal.ZERO;
        List<OrderQuoteItem> quoteItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            Map<String, Object> sku = skuMap.get(entry.getKey());
            if (sku == null) {
                throw new BusinessException("SKU_NOT_FOUND", "商品不存在: SKU " + entry.getKey());
            }
            if (!Integer.valueOf(1).equals(toInt(sku.get("status")))) {
                throw new BusinessException("SKU_OFF_SALE", "商品已下架: SKU " + entry.getKey());
            }
            BigDecimal price = toAmount(sku.get("price"), entry.getKey());

            OrderQuoteItem item = new OrderQuoteItem();
            item.setProductId(toLong(sku.get("productId")));
            item.setSkuId(entry.getKey());
            item.setProductName((String) sku.get("productName"));
            item.setSkuImage((String) sku.get("image"));
            item.setSkuAttributes((String) sku.get("attributes"));
            item.setPrice(price);
            item.setQuantity(entry.getValue());
            item.setSubtotal(price.multiply(BigDecimal.valueOf(entry.getValue())));
            quoteItems.add(item);
            total = total.add(item.getSubtotal());
        }

        // 优惠券折扣在报价阶段仅记录券 ID；金额计算沿用下单时的服务端校验逻辑
        //（折扣上限/舍入规则由 COUPON-01 统一收口，本阶段折扣记 0，下单时按券重算）
        quote.setTotalAmount(total);
        quote.setDiscountAmount(BigDecimal.ZERO);
        quote.setPayAmount(total);
        quoteMapper.insert(quote);

        for (OrderQuoteItem item : quoteItems) {
            item.setQuoteId(quote.getId());
            quoteItemMapper.insert(item);
        }
        log.info("[TRADE01] 报价已生成 quoteId={} userId={} lines={} total={}",
                quote.getId(), userId, quoteItems.size(), total);
        return quote;
    }

    @Override
    public QuoteDetail getQuote(Long userId, Long quoteId) {
        OrderQuote quote = quoteMapper.selectById(quoteId);
        // 他人报价/不存在一律 404（不泄露存在性）
        if (quote == null || !quote.getUserId().equals(userId)) {
            throw new BusinessException("QUOTE_NOT_FOUND", "报价不存在");
        }
        List<OrderQuoteItem> items = quoteItemMapper.selectList(
                new LambdaQueryWrapper<OrderQuoteItem>().eq(OrderQuoteItem::getQuoteId, quoteId));
        return new QuoteDetail(quote, items);
    }

    private Integer toInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private BigDecimal toAmount(Object value, Long skuId) {
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        throw new BusinessException("SKU_PRICE_INVALID", "商品价格数据异常: SKU " + skuId);
    }
}
