package com.cloudmart.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("order_items")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long productId;

    private Long skuId;

    private String productName;

    private String skuImage;

    private String skuAttributes;

    private BigDecimal price;

    private Integer quantity;

    /** 明细实付分摊（T04）：=成交价×数量-优惠分摊；NULL=历史单未准确分摊 */
    private BigDecimal payAmount;

    private LocalDateTime createdAt;
}
