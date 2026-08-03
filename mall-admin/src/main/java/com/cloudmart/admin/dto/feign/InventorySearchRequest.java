package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 库存搜索请求，与 mall-inventory 服务端 AdminInventoryController 查询参数对齐
 */
public record InventorySearchRequest(
    Long productId,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public InventorySearchRequest {
        if (page == null) { page = 1; }
        if (pageSize == null) { pageSize = 20; }
    }
}
