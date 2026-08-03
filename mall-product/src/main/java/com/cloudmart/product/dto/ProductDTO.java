package com.cloudmart.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDTO(
    Long id,
    String name,
    String description,
    Long categoryId,
    String categoryName,
    String brand,
    String mainImage,
    Integer status,
    List<SkuDTO> skus,
    LocalDateTime createdAt
) {}
