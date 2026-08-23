package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.CapsuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台时间胶囊统计 Controller（文档 Sprint 2.4 管理后台：胶囊统计）。
 *
 * <p>路由前缀 /admin/capsules，仅内部服务调用（mall-admin 经 Feign 代理
 * 转发，hasRole('INTERNAL') 由 X-Internal-Call 头授予）；权限点
 * {@code business:capsule:stats} 在管理后台角色界面配置。</p>
 */
@RestController
@RequestMapping("/admin/capsules")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-时间胶囊", description = "胶囊统计（创建数/开启数/到期数）")
@RequiredArgsConstructor
public class AdminCapsuleController {

    private final CapsuleService capsuleService;

    @GetMapping("/stats")
    @Operation(summary = "胶囊统计", description = "总量/各状态计数/今日创建；"
            + "通知推送记录经 mall-notification /admin/notifications 查看（type=CAPSULE_AVAILABLE）")
    public ApiResponse<Map<String, Object>> getStats() {
        return ApiResponse.ok(capsuleService.getAdminStats());
    }
}
