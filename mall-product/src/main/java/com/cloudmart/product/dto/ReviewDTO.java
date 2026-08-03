package com.cloudmart.product.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewDTO(
    Long id,
    Long productId,
    Long userId,
    String username,
    String userAvatar,
    Long orderId,
    Long skuId,
    String skuAttributes,
    Integer rating,
    String content,
    List<String> images,
    Integer status,
    LocalDateTime createdAt
) {}
