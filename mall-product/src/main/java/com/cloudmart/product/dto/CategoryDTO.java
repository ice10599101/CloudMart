package com.cloudmart.product.dto;

public record CategoryDTO(
    Long id,
    String name,
    Long parentId,
    Integer sortOrder,
    String icon,
    Integer status
) {}
