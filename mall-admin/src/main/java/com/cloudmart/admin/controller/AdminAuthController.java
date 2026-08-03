package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminPermissionsResponse;
import com.cloudmart.admin.dto.AdminValidateRequest;
import com.cloudmart.admin.dto.AdminValidateResponse;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.service.AdminAuthService;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Set;

@Tag(name = "管理后台认证", description = "管理后台登录、获取用户信息、菜单权限等接口")
@RestController
@RequestMapping("/auth")
public class AdminAuthController {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthController.class);

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/validate")
    @Operation(summary = "验证管理员凭据", description = "内部接口，网关调用验证用户名密码")
    public ApiResponse<AdminValidateResponse> validateAdmin(@RequestBody AdminValidateRequest request,
                                                             HttpServletRequest httpRequest) {
        log.info("validateAdmin called: account={}, password={}", request.account(), request.password() != null ? "***" : "NULL");

        String internalCall = httpRequest.getHeader(SecurityConstants.INTERNAL_CALL_HEADER);
        if (!"true".equals(internalCall)) {
            log.warn("validateAdmin: missing or invalid X-Internal-Call header");
            throw new BusinessException("FORBIDDEN", "内部接口禁止外部访问");
        }

        if (request.account() == null || request.password() == null) {
            log.warn("validateAdmin: account or password is null - account={}", request.account());
            throw new BusinessException("VALIDATION_ERROR", "用户名和密码不能为空");
        }

        AdminUser adminUser = adminAuthService.validateCredentials(request.account(), request.password());
        Set<String> permissions = adminAuthService.resolvePermissions(adminUser.getId());
        boolean isSuperAdmin = adminAuthService.checkSuperAdmin(adminUser.getId());

        AdminValidateResponse response = new AdminValidateResponse(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getNickname(),
                adminUser.getDeptId(),
                permissions,
                isSuperAdmin
        );

        return ApiResponse.ok(response);
    }

    @GetMapping("/permissions/{userId}")
    @Operation(summary = "获取用户权限", description = "内部接口，网关调用获取用户权限列表")
    public ApiResponse<AdminPermissionsResponse> getPermissions(@PathVariable Long userId,
                                                                 HttpServletRequest httpRequest) {
        String internalCall = httpRequest.getHeader(SecurityConstants.INTERNAL_CALL_HEADER);
        if (!"true".equals(internalCall)) {
            throw new BusinessException("FORBIDDEN", "内部接口禁止外部访问");
        }

        AdminUser adminUser = adminAuthService.getUserById(userId);
        Set<String> permissions = adminAuthService.resolvePermissions(userId);
        boolean isSuperAdmin = adminAuthService.checkSuperAdmin(userId);

        return ApiResponse.ok(new AdminPermissionsResponse(
                userId,
                adminUser.getUsername(),
                adminUser.getNickname(),
                adminUser.getDeptId(),
                permissions,
                isSuperAdmin
        ));
    }
}
