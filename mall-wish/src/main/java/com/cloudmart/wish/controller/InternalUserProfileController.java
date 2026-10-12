package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.UserStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * R22 用户画像内部端点：向其他域提供用户权威业务时区。
 *
 * <p>时区由用户在三端启动/变更时经 POST /wish/my/timezone 上报，落 wish_user_stat.timezone；
 * 缺省/未上报返回 Asia/Shanghai（与 {@code UserStatServiceImpl} 默认一致）。
 * 仅内部服务令牌可达（hasRole('INTERNAL')），外部请求 403。</p>
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "心愿宇宙·用户画像内部端点", description = "用户权威业务时区（R22 统一业务日期）")
public class InternalUserProfileController {

    private final UserStatService userStatService;

    @GetMapping("/{userId}/timezone")
    @Operation(summary = "查询用户业务时区（IANA）",
            description = "经济规则（签到/日配额/每日次数）业务日归属用；未上报默认 Asia/Shanghai")
    public ApiResponse<Map<String, String>> getTimezone(@PathVariable("userId") Long userId) {
        return ApiResponse.ok(Map.of("userId", String.valueOf(userId),
                "timezone", userStatService.getUserTimezone(userId)));
    }
}
