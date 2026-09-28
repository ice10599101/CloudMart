package com.cloudmart.user.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.user.dto.UserDTO;
import com.cloudmart.user.dto.ValidateRequest;
import com.cloudmart.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "内部-用户凭据", description = "认证服务专用的凭据验证（服务令牌可达）")
public class InternalUserController {

    private final UserService userService;

    @PostMapping("/validate")
    @Operation(summary = "验证用户凭据", description = "通过小答号或邮箱验证用户名密码，仅供认证服务调用")
    public ApiResponse<UserDTO> validateUser(@Valid @RequestBody ValidateRequest request) {
        return ApiResponse.ok(userService.validateUser(request));
    }
}
