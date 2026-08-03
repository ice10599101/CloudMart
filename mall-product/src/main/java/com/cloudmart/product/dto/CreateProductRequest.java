package com.cloudmart.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateProductRequest(
    @NotBlank String name,
    String description,
    @NotNull Long categoryId,
    String brand,
    String mainImage,
    @Valid List<CreateSkuRequest> skus
) {}
