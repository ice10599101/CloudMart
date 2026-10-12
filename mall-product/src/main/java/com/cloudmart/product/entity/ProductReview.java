package com.cloudmart.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("product_reviews")
public class ProductReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Long userId;

    private Long orderId;

    /** R08 订单行锚点（每已完成订单行唯一评价） */
    private Long orderItemId;

    private Long skuId;

    private Integer rating;

    private String content;

    private String images;

    private Integer status;

    /** R08 管理员下架/驳回理由（作者可见；null=正常） */
    private String hideReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
