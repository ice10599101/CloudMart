package com.cloudmart.order.service;

import com.cloudmart.order.entity.OrderQuote;
import com.cloudmart.order.entity.OrderQuoteItem;

import java.util.List;

/**
 * 服务端报价（TRADE-01）：价格由服务端从商品服务取权威值，
 * 前端提交的任何金额/商品名不参与记账。
 */
public interface QuoteService {

    /**
     * 生成报价：校验 SKU 存在且在售、数量 1-999、单次最多 100 行（合并重复 SKU），
     * 单价/商品名/图片/属性取自商品服务权威值，报价 5 分钟有效。
     *
     * @throws BusinessException QUOTE_EMPTY / QUOTE_TOO_MANY_ITEMS / INVALID_QUANTITY /
     *                          SKU_NOT_FOUND / SKU_OFF_SALE / PRODUCT_SERVICE_UNAVAILABLE
     */
    OrderQuote createQuote(Long userId, List<QuoteItemInput> items, Long couponId);

    /** 查询本人报价（含明细）；他人报价按不存在处理 */
    QuoteDetail getQuote(Long userId, Long quoteId);

    /** 报价入参：只有 skuId + quantity——金额与商品信息不接受客户端声明 */
    record QuoteItemInput(Long skuId, Integer quantity) {
    }

    /** 报价详情（含明细） */
    record QuoteDetail(OrderQuote quote, List<OrderQuoteItem> items) {
    }
}
