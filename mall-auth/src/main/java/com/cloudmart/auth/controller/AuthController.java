package com.cloudmart.auth.controller;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;
import com.cloudmart.auth.service.AuthService;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证管理", description = "用户登录、注册、Token刷新等认证接口")
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Parameter(description = "登录请求体") @Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @Parameter(description = "刷新令牌请求体") @Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        // SEC-02：登出必须由已验签的用户域令牌驱动（资源服务器保证 jwt 非空）；
        // 匿名/无令牌登出是"带合法 token 的无操作"的镜像——一律拒绝而非伪成功
        requireUserDomain(jwt);
        authService.logout(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("sid"));
        return ApiResponse.ok(null);
    }

    /** SEC-02：退出全部设备——认证状态版本递增 + 撤销全部刷新令牌家族 */
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        requireUserDomain(jwt);
        authService.logoutAll(Long.valueOf(jwt.getSubject()));
        return ApiResponse.ok(null);
    }

    private void requireUserDomain(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        if (!"user".equals(jwt.getClaimAsString("scope"))) {
            throw new BusinessException("FORBIDDEN", "身份域不匹配");
        }
    }
}
