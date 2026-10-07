package com.cloudmart.product.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * N-2 评价返星光：mall-wish 内部星光发放端点（REVIEW_REWARD 流水）。
 * 鉴权走 SEC-01 服务令牌（outbound-scopes 配置见 mall-product yml；
 * mall-wish 入站发行方白名单含 mall-product）。
 */
@FeignClient(name = "mall-wish", contextId = "productWishStarlightFeignClient",
        fallbackFactory = WishStarlightFeignClientFallbackFactory.class)
public interface WishStarlightFeignClient {

    @PostMapping("/internal/starlight/earn")
    ApiResponse<Map<String, Object>> earn(@RequestParam("userId") Long userId,
                                          @RequestParam("amount") Integer amount,
                                          @RequestParam("refId") Long refId,
                                          @RequestParam("operationId") String operationId,
                                          @RequestParam("source") String source);
}
