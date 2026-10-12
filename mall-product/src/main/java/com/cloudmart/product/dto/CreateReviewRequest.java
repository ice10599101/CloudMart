package com.cloudmart.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * R08 评价创建请求：orderItemId 为唯一资格锚点；orderId/productId/skuId 必须与
 * 订单行权威事实一致（服务端逐项比对，防交叉错配），客户端不得只决定 itemCode。
 */
public record CreateReviewRequest(
    @NotNull Long orderItemId,
    @NotNull Long orderId,
    @NotNull Long productId,
    @NotNull Long skuId,
    @NotNull @Min(1) @Max(5) Integer rating,
    @NotBlank @Size(max = 1000) String content,
    @Size(max = 9, message = "图片最多 9 张") List<String> images
) {}
