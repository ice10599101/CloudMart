package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(contextId = "orderCartFeignClient", name = "mall-cart", fallbackFactory = CartFeignClientFallbackFactory.class)
public interface CartFeignClient {

    @DeleteMapping("/checked")
    ApiResponse<Void> clearCheckedItems(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId);
}
