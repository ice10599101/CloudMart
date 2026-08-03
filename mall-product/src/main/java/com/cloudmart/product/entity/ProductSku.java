package com.cloudmart.product.entity;

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
@TableName("product_skus")
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private String skuCode;

    private String attributes;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private String image;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
