package com.cloudmart.admin.dto.feign;

public record BrandDTO(
    Long id,
    String name,
    String logo,
    String description,
    Integer status,
    String createdAt,
    String updatedAt
) {}
