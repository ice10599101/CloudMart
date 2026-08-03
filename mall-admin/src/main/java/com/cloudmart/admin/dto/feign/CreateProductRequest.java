package com.cloudmart.admin.dto.feign;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 创建商品请求，与 mall-product 服务端 CreateProductRequest 字段对齐
 */
public record CreateProductRequest(
    @NotBlank String name,
    String description,
    @NotNull Long categoryId,
    String brand,
    String mainImage,
    @Valid List<CreateSkuRequest> skus
) {}
