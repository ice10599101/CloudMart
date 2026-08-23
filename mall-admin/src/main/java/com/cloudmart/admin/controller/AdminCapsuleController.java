package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.CapsuleFeignClient;
import com.cloudmart.admin.feign.NotificationQueryFeignClient;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 时间胶囊管理代理接口（Sprint 2.4 管理后台：胶囊统计 + 通知推送记录查看）。
 *
 * <p>统计转发 mall-wish /admin/capsules/stats；推送记录转发
 * mall-notification /admin/notifications（type=CAPSULE_AVAILABLE 筛选
 * 胶囊到期推送）。权限点 {@code business:capsule:stats} 在管理后台
 * 角色界面配置。</p>
 */
@RestController
@Tag(name = "时间胶囊管理", description = "胶囊统计（创建数/开启数/到期数）+ 通知推送记录查看")
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
}
