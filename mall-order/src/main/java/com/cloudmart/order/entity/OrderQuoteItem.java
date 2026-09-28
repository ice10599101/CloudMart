package com.cloudmart.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 报价明细快照（TRADE-01）：单价/属性在报价时锁定，下单直接复制进订单项。 */
@Data
@TableName("order_quote_item")
public class OrderQuoteItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long quoteId;

    private Long productId;

    private Long skuId;

    private String productName;

    private String skuImage;

    private String skuAttributes;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal subtotal;

    private LocalDateTime createdAt;
}
