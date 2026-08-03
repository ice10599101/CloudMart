package com.cloudmart.admin.dto.feign;

/**
 * 分类 Feign 传输对象，与 mall-product 服务端 CategoryDTO 字段对齐
 */
public record CategoryDTO(
    Long id,
    String name,
    Long parentId,
    Integer sortOrder,
    String icon,
    Integer status
) {}
