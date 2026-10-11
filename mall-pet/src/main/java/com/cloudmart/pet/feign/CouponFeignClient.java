package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.dto.ExchangeCodeFactDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * mall-coupon 内部客户端（R01）：兑换码权威事实只读核验。
 * 出站签名由通用 ServiceTokenFeignInterceptor 按
 * {@code cloudmart.security.outbound-scopes[mall-coupon]=coupon:redeem} 附加；
 * mall-coupon 以最长前缀规则校验 iss=mall-pet、scope=coupon:redeem。
 */
@FeignClient(contextId = "petCouponFeignClient", name = "mall-coupon",
        fallbackFactory = CouponFeignClientFallbackFactory.class)
public interface CouponFeignClient {

    @GetMapping("/internal/exchange-codes/{code}/fact")
    ApiResponse<ExchangeCodeFactDTO> getExchangeCodeFact(@PathVariable("code") String code);
}
