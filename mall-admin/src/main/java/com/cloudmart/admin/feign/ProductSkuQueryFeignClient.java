package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.SkuBatchDTO;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * T12：商品只读批量查询（独立 base=/products）。
 *
 * <p>不能复用 {@link ProductFeignClient}——它类级 `path=/admin/products`，方法再写
 * `/skus/batch` 会变成 `/admin/products/skus/batch`（该端点不存在，库存名称因此永久降级）。
 * 商品服务真实端点是 `/products/skus/batch`（公开只读，供跨服务 enrich）。</p>
 */
@FeignClient(contextId = "adminProductSkuQueryFeignClient", name = "mall-product", path = "/products")
public interface ProductSkuQueryFeignClient {

    @GetMapping("/skus/batch")
    ApiResponse<List<SkuBatchDTO>> getSkusBatch(@RequestParam("ids") List<Long> ids);
}
