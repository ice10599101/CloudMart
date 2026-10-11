package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.dto.SkuBatchItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品 SKU 查询客户端（TRADE-01）：报价从服务端取权威价格/销售状态，
 * 前端提交的任何金额一律丢弃。请求由出站服务令牌拦截器按
 * {@code outbound-scopes[mall-product]=product:read} 签名。
 */
@FeignClient(contextId = "orderProductFeignClient", name = "mall-product", path = "/products",
        fallbackFactory = ProductFeignClientFallbackFactory.class)
public interface ProductFeignClient {

    @GetMapping("/skus/batch")
    ApiResponse<List<SkuBatchItemDTO>> getSkusBatch(@RequestParam("ids") List<Long> ids);
}
