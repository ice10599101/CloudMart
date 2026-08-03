package com.cloudmart.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReviewRequest(
    @NotNull Long orderId,
    @NotNull Long productId,
    @NotNull Long skuId,
    @NotNull @Min(1) @Max(5) Integer rating,
    @NotBlank @Size(max = 1000) String content,
    List<String> images
) {}
