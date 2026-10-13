package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.SendNotificationRequest;
import com.cloudmart.admin.feign.NotificationFeignClient;
import com.cloudmart.admin.feign.NotificationQueryFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务管理-通知域：站内通知查询、发送、撤回。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 * 与 {@link AdminNoticeController}（通知公告内容管理）区分：本类面向发给用户的通知实例。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：用户通知")
public class AdminBusinessNotificationController {

    private final NotificationFeignClient notificationFeignClient;
    private final NotificationQueryFeignClient notificationQueryFeignClient;

    public AdminBusinessNotificationController(NotificationFeignClient notificationFeignClient,
                                               NotificationQueryFeignClient notificationQueryFeignClient) {
        this.notificationFeignClient = notificationFeignClient;
        this.notificationQueryFeignClient = notificationQueryFeignClient;
    }

    // ==================== 通知 ====================

    @GetMapping("/notifications")
    @RequiresPermission("business:notification:list")
    @Operation(summary = "通知列表", description = "查询通知列表")
    public ApiResponse<?> listNotifications(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return notificationQueryFeignClient.listNotifications(userId, type, page, pageSize);
    }

    @DeleteMapping("/notifications/{notificationId}")
    @OperLog(title = "通知管理", businessType = 3)
    @RequiresPermission("business:notification:list")
    @Operation(summary = "删除通知", description = "管理后台删除指定通知（撤回误发内容）")
    public ApiResponse<Void> deleteNotification(@PathVariable Long notificationId) {
        return notificationFeignClient.deleteNotification(notificationId);
    }

    @PostMapping("/notifications")
    @OperLog(title = "通知管理", businessType = 1)
    @RequiresPermission("business:notification:send")
    @Operation(summary = "发送通知", description = "发送通知给用户")
    public ApiResponse<Object> sendNotification(@Valid @RequestBody SendNotificationRequest request) {
        return notificationFeignClient.sendNotification(request);
    }
}
