package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.CapsuleFeignClient;
import com.cloudmart.admin.feign.NotificationQueryFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 时间胶囊管理代理接口（Sprint 2.4 统计/推送记录 + R17 公共墙审核闭环）。
 *
 * <p>统计转发 mall-wish /admin/capsules/stats；推送记录转发
 * mall-notification /admin/notifications（type=CAPSULE_AVAILABLE 筛选
 * 胶囊到期推送）；墙审核/下架转发 mall-wish /admin/capsules/wall/**。
 * 裁决管理员身份由 AdminFeignInterceptor 统一注入 X-User-Id。</p>
 */
@RestController
@Tag(name = "时间胶囊管理", description = "胶囊统计 + 公共墙审核/下架 + 通知推送记录")
@RequiredArgsConstructor
public class AdminCapsuleController {

    private final CapsuleFeignClient capsuleFeignClient;
    private final NotificationQueryFeignClient notificationQueryFeignClient;

    @GetMapping("/wish/capsules/stats")
    @RequiresPermission("business:capsule:stats")
    @Operation(summary = "胶囊统计", description = "总量/各状态计数（SEALED/AVAILABLE/OPENED/CANCELLED）/今日创建")
    public ApiResponse<Object> getCapsuleStats() {
        return capsuleFeignClient.getStats();
    }

    @GetMapping("/wish/capsules/notifications")
    @RequiresPermission("business:capsule:stats")
    @Operation(summary = "通知推送记录", description = "转发 mall-notification 通知列表；"
            + "type=CAPSULE_AVAILABLE 为胶囊到期推送（其余 type 为全站通知记录）")
    public ApiResponse<List<Map<String, Object>>> listPushRecords(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return notificationQueryFeignClient.listNotifications(userId, type, page, pageSize);
    }

    // ========== R17 公共胶囊墙审核 ==========

    @GetMapping("/wish/capsules/wall/pending")
    @RequiresPermission("business:capsule:audit")
    @Operation(summary = "胶囊墙待审核列表", description = "wallStatus=1 按申请时间倒序，含作者 ID")
    public ApiResponse<Object> wallPending(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return capsuleFeignClient.wallPending(page, pageSize);
    }

    @GetMapping("/wish/capsules/wall/approved")
    @RequiresPermission("business:capsule:audit")
    @Operation(summary = "胶囊墙已上墙列表", description = "wallStatus=2 按决定时间倒序，供下架管理")
    public ApiResponse<Object> wallApproved(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return capsuleFeignClient.wallApproved(page, pageSize);
    }

    @PostMapping("/wish/capsules/wall/{id}/decide")
    @OperLog(title = "胶囊墙审核", businessType = 2)
    @RequiresPermission("business:capsule:audit")
    @Operation(summary = "胶囊墙裁决", description = "通过上墙或驳回（驳回 reason 必填）；"
            + "并发重复裁决由下游返回 409")
    public ApiResponse<Object> decideWall(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return capsuleFeignClient.decideWall(id, body);
    }

    @PostMapping("/wish/capsules/wall/{id}/take-down")
    @OperLog(title = "胶囊墙下架", businessType = 2)
    @RequiresPermission("business:capsule:audit")
    @Operation(summary = "胶囊墙下架", description = "将已上墙内容下架（reason 必填），"
            + "公开墙即刻不可见，理由随通知告知作者")
    public ApiResponse<Object> takeDownWall(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return capsuleFeignClient.takeDownWall(id, body);
    }
}
