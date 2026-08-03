package com.cloudmart.notification.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.notification.converter.NotificationConverter;
import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.dto.SendNotificationRequest;
import com.cloudmart.notification.service.NotificationService;
import com.cloudmart.notification.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/notifications")
@Tag(name = "通知管理(后台)", description = "管理后台通知管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final NotificationConverter notificationConverter;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询通知列表", description = "管理后台分页查询通知列表，支持按用户ID和类型筛选")
    public ApiResponse<List<NotificationVO>> listNotifications(
            @Parameter(description = "用户ID") @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "通知类型") @RequestParam(value = "type", required = false) String type,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        List<NotificationDTO> dtos = notificationService.listAllNotifications(userId, type, page, pageSize);
        return ApiResponse.ok(notificationConverter.dtoListToVOList(dtos));
    }

    @PostMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "发送通知", description = "管理后台发送通知给指定用户")
    public ApiResponse<NotificationVO> sendNotification(@Valid @RequestBody SendNotificationRequest request) {
        NotificationDTO dto = notificationService.sendNotification(request);
        return ApiResponse.ok(notificationConverter.dtoToVO(dto));
    }

    @PostMapping("/user")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "发送用户通知", description = "管理后台向指定用户发送通知")
    public ApiResponse<Void> sendNotificationToUser(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "通知类型") @RequestParam String type,
            @Parameter(description = "通知标题") @RequestParam String title,
            @Parameter(description = "通知内容") @RequestParam String content,
            @Parameter(description = "关联业务ID") @RequestParam(required = false) Long bizId,
            @Parameter(description = "关联业务类型") @RequestParam(required = false) String bizType) {
        notificationService.sendNotificationToUser(userId, type, title, content, bizId, bizType);
        return ApiResponse.ok(null);
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "广播通知", description = "管理后台向全体用户发送广播通知")
    public ApiResponse<Void> broadcastNotification(
            @Parameter(description = "通知类型") @RequestParam String type,
            @Parameter(description = "通知标题") @RequestParam String title,
            @Parameter(description = "通知内容") @RequestParam String content) {
        notificationService.broadcastNotification(type, title, content);
        return ApiResponse.ok(null);
    }
}
