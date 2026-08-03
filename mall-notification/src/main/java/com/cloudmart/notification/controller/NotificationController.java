package com.cloudmart.notification.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.notification.converter.NotificationConverter;
import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.dto.UnreadCountDTO;
import com.cloudmart.notification.service.NotificationService;
import com.cloudmart.notification.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@Tag(name = "通知管理", description = "通知查询、标记已读接口")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationConverter notificationConverter;

    public NotificationController(NotificationService notificationService, NotificationConverter notificationConverter) {
        this.notificationService = notificationService;
        this.notificationConverter = notificationConverter;
    }

    @GetMapping
    @Operation(summary = "通知列表", description = "分页查询当前用户的通知列表，支持按类型筛选")
    public ApiResponse<List<NotificationVO>> listNotifications(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @Parameter(description = "通知类型筛选: LIKE/COMMENT/COLLECT/FOLLOW/SHARE/SYSTEM/BADGE") @RequestParam(value = "type", required = false) String type) {
        List<NotificationDTO> dtos;
        if (type != null && !type.isBlank()) {
            dtos = notificationService.listNotificationsByType(userId, type, page, pageSize);
        } else {
            dtos = notificationService.listNotifications(userId, page, pageSize);
        }
        return ApiResponse.ok(notificationConverter.dtoListToVOList(dtos));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读数量", description = "查询当前用户的未读通知数量")
    public ApiResponse<Map<String, Long>> getUnreadCount(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        UnreadCountDTO dto = notificationService.getUnreadCount(userId);
        return ApiResponse.ok(Map.of("count", dto.count()));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "标记已读", description = "将指定通知标记为已读")
    public ApiResponse<Void> markAsRead(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "通知ID") @PathVariable("notificationId") Long notificationId) {
        notificationService.markAsRead(userId, notificationId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/read-all")
    @Operation(summary = "全部已读", description = "将当前用户的所有通知标记为已读")
    public ApiResponse<Void> markAllAsRead(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        notificationService.markAllAsRead(userId);
        return ApiResponse.ok(null);
    }
}
