package com.cloudmart.product.dto;

import jakarta.validation.Valid;

import java.util.List;

public record UpdateProductRequest(
    String name,
    String description,
    Long categoryId,
    String brand,
    String mainImage,
    Integer status,
    @Valid List<CreateSkuRequest> skus
) {}
