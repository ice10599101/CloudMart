package com.cloudmart.user.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.user.dto.UserDTO;
import com.cloudmart.user.dto.ValidateRequest;
import com.cloudmart.user.service.AddressService;
import com.cloudmart.user.service.UserService;
import com.cloudmart.user.vo.ShippingAddressVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户服务内部接口（SEC-04）：仅限 mall-auth 等持服务令牌的调用方
 * （ROLE_INTERNAL），不再暴露匿名公开的凭据验证入口。
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "内部-用户接口", description = "认证服务/订单系统专用（服务令牌可达）")
public class InternalUserController {

    private final UserService userService;
    private final AddressService addressService;

    @PostMapping("/validate")
    @Operation(summary = "验证用户凭据", description = "通过小答号或邮箱验证用户名密码，仅供认证服务调用")
    public ApiResponse<UserDTO> validateUser(@Valid @RequestBody ValidateRequest request) {
        return ApiResponse.ok(userService.validateUser(request));
    }

    /**
     * T09/T10：系统建单（秒杀/拼团消费者）取用户默认收货地址——无默认地址
     * 返回 null（调用方以 ADDRESS_REQUIRED 业务失败处置）。
     */
    @GetMapping("/{userId}/default-address")
    @Operation(summary = "默认收货地址", description = "按用户 ID 查询默认收货地址；无则返回 null（服务令牌可达）")
    public ApiResponse<ShippingAddressVO> defaultAddress(@PathVariable("userId") Long userId) {
        return ApiResponse.ok(addressService.getDefaultAddress(userId));
    }
}
