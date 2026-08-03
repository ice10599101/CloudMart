package com.cloudmart.admin.dto.feign;

/**
 * 库存 Feign 传输对象，与 mall-inventory 服务端 InventoryDTO 字段对齐
 */
public record InventoryDTO(
    Long id,
    Long productId,
    Long skuId,
    Integer available,
    Integer reserved
) {}
