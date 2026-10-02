package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.dto.UserDefaultAddressDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * mall-user 内部地址查询客户端（T09/T10）：系统建单（秒杀/拼团消费者）
 * 取用户默认收货地址——orders.receiver_* NOT NULL，系统单必须填充收货人。
 */
@FeignClient(contextId = "userAddressFeignClient", name = "mall-user",
        fallbackFactory = UserAddressFeignClientFallbackFactory.class)
public interface UserAddressFeignClient {

    @GetMapping("/internal/users/{userId}/default-address")
    ApiResponse<UserDefaultAddressDTO> getDefaultAddress(@PathVariable("userId") Long userId);
}
