package com.cloudmart.admin.dto.feign;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价 Feign 传输对象，与 mall-product 服务端 ReviewDTO 字段对齐
 */
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
    LocalDateTime createdAt
) {}
