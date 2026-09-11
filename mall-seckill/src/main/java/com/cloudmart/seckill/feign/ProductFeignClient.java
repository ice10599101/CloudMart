package com.cloudmart.seckill.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(contextId = "seckillProductFeignClient", name = "mall-product", path = "/products")
public interface ProductFeignClient {

    /**
     * 按 SKU 批量查询商品名称/图片，用于秒杀商品列表 enrich。
     */
    @GetMapping("/skus/batch")
    ApiResponse<List<SkuBatchItem>> getSkusBatch(@RequestParam("ids") List<Long> ids);

    record SkuBatchItem(Long skuId, Long productId, String productName, String image) {}
}
