package com.cloudmart.marketing.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * T10：试算取价——满减预览需要把 SKU+数量换算成购物篮金额，价格必须取商品权威值，
 * 不接受调用方传入的价格。{@code /products/skus/batch} 为公开只读接口（服务内部调用）。
 */
@FeignClient(contextId = "marketingProductSkuFeignClient", name = "mall-product", path = "/products")
public interface ProductSkuFeignClient {

    @GetMapping("/skus/batch")
    ApiResponse<List<SkuBatchItem>> getSkusBatch(@RequestParam("ids") List<Long> ids);

    /** 商品 SKU 批量快照（只声明试算需要的字段）。 */
    record SkuBatchItem(Long skuId, Long productId, String productName, String image,
                        BigDecimal price, Integer status) {
    }
}
