package com.cloudmart.admin.dto.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * 库存行响应对象（TRADE-02）：服务端库存事实 + 商品名/SKU 规格展示补齐。
 * <p>合计口径：无“总库存”字段——库存表只有 available（可售）与 lockedStock（预占），
 * 不存在并不存在的多仓/安全库存字段。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryRowDTO(
    Long id,
    Long productId,
    Long skuId,
    /** 批量补齐的商品名称（商品服务不可用时为 null，不阻断库存事实返回） */
    String productName,
    /** 批量补齐的 SKU 规格（attributes） */
    String skuSpec,
    Integer availableStock,
    Integer lockedStock,
    /** 乐观锁版本（调整单 CAS 前置条件，前端展示与提交） */
    Integer version,
    LocalDateTime updatedAt
) {}
