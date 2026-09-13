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
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "status", required = false) Integer status);

    @GetMapping("/{id}")
    ApiResponse<UserDTO> getUserById(@PathVariable("id") Long id);

    @PutMapping("/{id}")
    ApiResponse<UserDTO> updateUser(@PathVariable("id") Long id, @RequestBody AdminUpdateUserRequest request);

    @PutMapping("/{id}/status")
    ApiResponse<Void> toggleUserStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status);

    /** 管理员重置会员密码（mall-user PUT /admin/users/{id}/password，无需原密码） */
    @PutMapping("/{id}/password")
    ApiResponse<Void> resetPassword(@PathVariable("id") Long id, @RequestBody java.util.Map<String, String> body);

    @GetMapping("/count")
    ApiResponse<CountResponse> getMemberCount();
}
