package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.service.PrivacyService;
import com.cloudmart.community.vo.PrivacyVisibility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部隐私可见性接口（供 mall-user 他人资料接口脱敏调用）。
 *
 * <p>路由前缀 /internal/privacy，仅内部服务可达（mall-user 经 Feign 转发，
 * hasRole('INTERNAL') 由 X-Internal-Call 头经 InternalCallAuthenticationFilter 授予）。
 * 查看者身份通过 X-User-Id 头透传。</p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/privacy")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "内部-资料可见性", description = "mall-user 他人资料敏感字段脱敏判断")
public class InternalPrivacyController {

    private final PrivacyService privacyService;

    @GetMapping("/{targetUserId}")
    @Operation(summary = "查询字段可见性", description = "根据查看者（X-User-Id 头）与目标用户关系，返回生日/邮箱可见性")
    public ApiResponse<PrivacyVisibility> checkVisibility(
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long viewerUserId,
            @PathVariable Long targetUserId) {
        PrivacyVisibility visibility = privacyService.checkVisibility(viewerUserId, targetUserId);
        log.info("隐私可见性查询, viewer={}, target={}, birthday={}, email={}, followers={}, following={}, collections={}, posts={}",
                viewerUserId, targetUserId, visibility.birthdayVisible(), visibility.emailVisible(),
                visibility.followersVisible(), visibility.followingVisible(),
                visibility.collectionsVisible(), visibility.postsVisible());
        return ApiResponse.ok(visibility);
    }
}