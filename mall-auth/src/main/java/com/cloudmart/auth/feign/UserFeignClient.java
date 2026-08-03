package com.cloudmart.auth.feign;

import com.cloudmart.auth.dto.UserDTO;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(contextId = "authUserFeignClient", name = "mall-user", path = "/users", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    @PostMapping("/validate")
    ApiResponse<UserDTO> validateUser(@RequestBody ValidateRequest request);
}
