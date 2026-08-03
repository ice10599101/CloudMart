package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.service.UserSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/settings")
@Tag(name = "用户设置", description = "用户偏好与隐私设置接口")
@RequiredArgsConstructor
public class UserSettingController {

    private final UserSettingService userSettingService;

    @GetMapping
    @Operation(summary = "获取用户设置", description = "获取当前用户的通知偏好和隐私设置")
    public ApiResponse<Map<String, String>> getUserSettings(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        Map<String, String> settings = userSettingService.getUserSettings(userId);
        return ApiResponse.ok(settings);
    }

    @PutMapping
    @Operation(summary = "更新用户设置", description = "更新当前用户的通知偏好和隐私设置")
    public ApiResponse<Void> updateUserSettings(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody Map<String, String> settings) {
        userSettingService.updateUserSettings(userId, settings);
        return ApiResponse.ok(null);
    }
}
