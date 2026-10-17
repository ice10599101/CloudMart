package com.cloudmart.admin.dto.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * 库存 Feign 传输对象，与 mall-inventory 服务端 InventoryVO 字段对齐。
 * <p>TRADE-02 修复：旧 DTO 用 available/reserved 命名，而服务端序列化为
 * availableStock/lockedStock，Jackson 静默丢弃未知字段导致数值恒为 null；
 * version 供前端调整单乐观锁 CAS 使用。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryDTO(
    Long id,
    Long productId,
    Long skuId,
    Integer availableStock,
    Integer lockedStock,
    Integer version,
    LocalDateTime updatedAt
) {}
