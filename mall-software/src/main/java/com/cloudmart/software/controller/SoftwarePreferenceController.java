package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.service.SoftwareSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloudmart.common.constant.SecurityConstants;

import java.util.Map;

/**
 * 软件更新提醒偏好（方案 7.3，P1）：GET 读取（缺省开启）、PUT 修改（CAS 可选）。
 * 固定路径必须在 SecurityConfig 中先于 /software/{id} 声明（避免落入匿名通配）。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "小答软件-提醒偏好", description = "软件更新提醒开关（P1）")
public class SoftwarePreferenceController {

    private final SoftwareSubscriptionService subscriptionService;

    @GetMapping("/preferences")
    @Operation(summary = "读取提醒偏好", description = "无记录视为开启（updateNotifications=true）")
    public ApiResponse<Map<String, Object>> preferences(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(subscriptionService.preferences(userId));
    }

    @PutMapping("/preferences")
    @Operation(summary = "修改提醒偏好", description = "updateNotifications、expectedVersion?；无记录首建")
    public ApiResponse<Map<String, Object>> updatePreferences(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody Map<String, Object> body) {
        Object value = body == null ? null : body.get("updateNotifications");
        if (!(value instanceof Boolean enabled)) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_PREFERENCE_INVALID", "缺少 updateNotifications");
        }
        Integer expectedVersion = body.get("expectedVersion") instanceof Number number
                ? number.intValue() : null;
        return ApiResponse.ok(
                subscriptionService.updatePreferences(userId, enabled, expectedVersion));
    }
}
