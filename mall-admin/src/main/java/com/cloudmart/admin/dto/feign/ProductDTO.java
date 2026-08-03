package com.cloudmart.admin.dto.feign;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品 Feign 传输对象，与 mall-product 服务端 ProductDTO 字段对齐
 */
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
