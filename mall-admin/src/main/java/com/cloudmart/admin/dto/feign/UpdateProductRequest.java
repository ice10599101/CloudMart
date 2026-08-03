package com.cloudmart.admin.dto.feign;

import jakarta.validation.Valid;

import java.util.List;

/**
 * 更新商品请求，与 mall-product 服务端 UpdateProductRequest 字段对齐
 */
public record UpdateProductRequest(
    String name,
    String description,
    Long categoryId,
    String brand,
    String mainImage,
    Integer status,
    @Valid List<CreateSkuRequest> skus
) {}
