package com.cloudmart.auth.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;
import com.cloudmart.auth.service.AdminAuthService;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端认证", description = "管理员登录、Token刷新等认证接口")
@RestController
@RequestMapping("/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminAuthService.login(request, httpRequest));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(adminAuthService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        // SEC-02：登出必须由已验签的管理员域令牌驱动；匿名登出一律拒绝而非伪成功
        requireAdminDomain(jwt);
        adminAuthService.logout(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("sid"));
        return ApiResponse.ok(null);
    }

    /** SEC-02：退出全部设备——认证状态版本递增 + 撤销全部管理员刷新家族 */
    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        requireAdminDomain(jwt);
        adminAuthService.logoutAll(Long.valueOf(jwt.getSubject()));
        return ApiResponse.ok(null);
    }

    private void requireAdminDomain(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        if (!"admin".equals(jwt.getClaimAsString("scope"))) {
            throw new BusinessException("FORBIDDEN", "身份域不匹配");
        }
    }
}
