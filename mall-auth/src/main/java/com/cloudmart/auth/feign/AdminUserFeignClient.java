package com.cloudmart.auth.feign;

import com.cloudmart.auth.dto.AdminUserDTO;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(contextId = "adminUserFeignClient", name = "mall-admin", path = "/auth", fallbackFactory = AdminUserFeignClientFallbackFactory.class)
public interface AdminUserFeignClient {

    @PostMapping("/validate")
    ApiResponse<AdminUserDTO> validateAdmin(@RequestBody ValidateRequest request);

    @GetMapping("/permissions/{userId}")
    ApiResponse<AdminUserDTO> getPermissionsByUserId(@PathVariable("userId") Long userId);
}
