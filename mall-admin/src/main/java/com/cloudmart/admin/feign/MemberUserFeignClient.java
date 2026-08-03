package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.AdminUpdateUserRequest;
import com.cloudmart.admin.dto.feign.CountResponse;
import com.cloudmart.admin.dto.feign.UserDTO;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(contextId = "memberUserFeignClient", name = "mall-user", path = "/admin/users", fallbackFactory = MemberUserFeignClientFallbackFactory.class)
public interface MemberUserFeignClient {

    @GetMapping
    ApiResponse<List<UserDTO>> listUsers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/{id}")
    ApiResponse<UserDTO> getUserById(@PathVariable("id") Long id);

    @PutMapping("/{id}")
    ApiResponse<UserDTO> updateUser(@PathVariable("id") Long id, @RequestBody AdminUpdateUserRequest request);

    @PutMapping("/{id}/status")
    ApiResponse<Void> toggleUserStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status);

    @GetMapping("/count")
    ApiResponse<CountResponse> getMemberCount();
}
