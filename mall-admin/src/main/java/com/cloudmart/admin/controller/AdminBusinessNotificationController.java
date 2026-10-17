package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.SendNotificationRequest;
import com.cloudmart.admin.feign.NotificationFeignClient;
import com.cloudmart.admin.feign.NotificationQueryFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.admin.dto.feign.BroadcastJobCreateRequest;
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

    // ==================== ADMIN-03：群发批次（受众固定全站，禁用账号跳过） ====================

    @PostMapping("/notifications/broadcast-jobs")
    @OperLog(title = "通知群发", businessType = 1)
    @RequiresPermission("business:notification:send")
    @Operation(summary = "提交群发批次", description = "requestId 幂等：同键重复提交返回原批次；"
            + "返回批次号与目标/成功/失败/跳过计数")
    public ApiResponse<Object> createBroadcastJob(@Valid @RequestBody BroadcastJobCreateRequest request) {
        return notificationFeignClient.createBroadcastJob(request);
    }

    @GetMapping("/notifications/broadcast-jobs")
    @RequiresPermission("business:notification:batch:list")
    @Operation(summary = "群发批次列表", description = "独立批次查看权限（与发送权限分离）")
    public ApiResponse<Object> listBroadcastJobs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return notificationFeignClient.listBroadcastJobs(page, size);
    }

    @GetMapping("/notifications/broadcast-jobs/{jobNo}")
    @RequiresPermission("business:notification:batch:list")
    @Operation(summary = "群发批次详情", description = "批次 + 明细（上限 500 行）")
    public ApiResponse<Object> broadcastJobDetail(@PathVariable String jobNo) {
        return notificationFeignClient.broadcastJobDetail(jobNo);
    }

    @PostMapping("/notifications/broadcast-jobs/{jobNo}/retry")
    @OperLog(title = "通知群发重试", businessType = 2)
    @RequiresPermission("business:notification:send")
    @Operation(summary = "重试失败项", description = "仅补 FAILED 项；notification_id 幂等，不重复投递")
    public ApiResponse<Object> retryBroadcastJob(@PathVariable String jobNo) {
        return notificationFeignClient.retryBroadcastJob(jobNo);
    }
}
