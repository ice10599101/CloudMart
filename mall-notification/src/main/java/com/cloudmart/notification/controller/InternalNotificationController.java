package com.cloudmart.notification.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面向内部微服务的通知查询端点（mall-pet Feign 调用：宠物口吻提醒列表/未读数）。
 *
 * <p>安全：{@code hasRole('INTERNAL')}——仅携带 {@code X-Internal-Call: true} 的
 * 内部请求可达（见 InternalCallAuthenticationFilter），外部请求 403。
 * userId 必须显式传参（内部调用上下文无网关 X-User-Id 语义，身份由调用方服务担保）。</p>
 */
@RestController
@RequestMapping("/internal/notifications")
@Tag(name = "通知·内部端点", description = "mall-pet 专用：按用户查询通知列表/未读数（外部不可达）")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "按用户查询通知", description = "供宠物提醒列表使用；支持类型过滤与 offset 分页")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<NotificationDTO>> listByUser(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        List<NotificationDTO> dtos = type != null && !type.isBlank()
                ? notificationService.listNotificationsByType(userId, type, page, pageSize)
                : notificationService.listNotifications(userId, page, pageSize);
        return ApiResponse.ok(dtos);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "按用户查询未读数", description = "支持类型过滤（type=PET 为宠物口吻提醒未读数）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Long> unreadCount(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "type", required = false) String type) {
        return ApiResponse.ok(notificationService.countUnreadByType(userId, type));
    }
}
