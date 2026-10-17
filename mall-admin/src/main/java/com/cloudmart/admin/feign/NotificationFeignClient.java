package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.BroadcastJobCreateRequest;
import com.cloudmart.admin.dto.feign.BroadcastNotificationRequest;
import com.cloudmart.admin.dto.feign.SendNotificationRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(contextId = "notificationFeignClient", name = "mall-notification", path = "/admin/notifications", fallbackFactory = NotificationFeignClientFallbackFactory.class)
public interface NotificationFeignClient {

    @PostMapping
    ApiResponse<Object> sendNotification(@RequestBody SendNotificationRequest request);

    /** 全站广播：由 mall-notification 枚举全量会员逐用户落库 + WS 推送（JSON body，防 XssFilter 转义与 URL 截断） */
    @PostMapping("/broadcast")
    ApiResponse<Object> broadcastNotification(@RequestBody BroadcastNotificationRequest request);

    /** 管理端删除指定通知（撤回误发内容） */
    @DeleteMapping("/{notificationId}")
    ApiResponse<Void> deleteNotification(@PathVariable("notificationId") Long notificationId);

    // ---- ADMIN-03：群发批次任务 ----

    /** 提交群发批次（requestId 幂等；返回批次号与目标/成功/失败/跳过计数） */
    @PostMapping("/broadcast-jobs")
    ApiResponse<Object> createBroadcastJob(@RequestBody BroadcastJobCreateRequest request);

    @GetMapping("/broadcast-jobs")
    ApiResponse<Object> listBroadcastJobs(@RequestParam("page") Integer page,
                                          @RequestParam("size") Integer size);

    @GetMapping("/broadcast-jobs/{jobNo}")
    ApiResponse<Object> broadcastJobDetail(@PathVariable("jobNo") String jobNo);

    /** 重试失败项（notification_id 幂等，不重复投递） */
    @PostMapping("/broadcast-jobs/{jobNo}/retry")
    ApiResponse<Object> retryBroadcastJob(@PathVariable("jobNo") String jobNo);
}
