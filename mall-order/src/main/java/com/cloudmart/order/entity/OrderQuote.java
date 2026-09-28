package com.cloudmart.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务端报价单（TRADE-01）：价格快照由服务端生成并锁定有效期，
 * 下单以报价为准，前端提交的任何金额一律丢弃。
 */
@Data
@TableName("order_quote")
public class OrderQuote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer version;

    /** ACTIVE / CONSUMED / EXPIRED */
    private String status;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    private BigDecimal payAmount;

    private Long couponId;

    private LocalDateTime expiresAt;

    /** 消费该报价的订单ID（防止一报价多单） */
    private Long consumedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
