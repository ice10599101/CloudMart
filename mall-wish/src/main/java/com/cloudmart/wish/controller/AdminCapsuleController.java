package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.service.CapsuleService;
import com.cloudmart.wish.vo.AdminCapsuleWallVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台时间胶囊 Controller（Sprint 2.4 统计 + R17 公共胶囊墙审核闭环）。
 *
 * <p>路由前缀 /admin/capsules，仅内部服务调用（mall-admin 经 Feign 代理
 * 转发，hasRole('INTERNAL') 由服务令牌经 ServiceTokenAuthenticationFilter 授予）；
 * 裁决管理员身份经 {@code X-User-Id} 头透传（AdminFeignInterceptor），
 * 不接受请求体传入 actor。</p>
 */
@RestController
@RequestMapping("/admin/capsules")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-时间胶囊", description = "胶囊统计 + 公共墙审核/下架（R17）")
@RequiredArgsConstructor
public class AdminCapsuleController {

    private final CapsuleService capsuleService;

    @GetMapping("/stats")
    @Operation(summary = "胶囊统计", description = "总量/各状态计数/今日创建；"
            + "通知推送记录经 mall-notification /admin/notifications 查看（type=CAPSULE_AVAILABLE）")
    public ApiResponse<Map<String, Object>> getStats() {
        return ApiResponse.ok(capsuleService.getAdminStats());
    }

    /** R17 审核请求：驳回时 reason 必填（≤200 字），通过时可空。 */
    public record WallDecisionRequest(boolean approve, String reason) {
    }

    /** R17 下架请求：reason 必填（≤200 字），随通知回传作者。 */
    public record WallTakeDownRequest(String reason) {
    }

    @GetMapping("/wall/pending")
    @Operation(summary = "胶囊墙待审核列表", description = "R17：wallStatus=1 按申请时间倒序，含作者 ID")
    public ApiResponse<com.baomidou.mybatisplus.extension.plugins.pagination.Page<AdminCapsuleWallVO>> wallPending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(capsuleService.wallPending(page, Math.min(size, 50)));
    }

    @GetMapping("/wall/approved")
    @Operation(summary = "胶囊墙已上墙列表", description = "R17：wallStatus=2 按决定时间倒序，供管理员下架管理")
    public ApiResponse<com.baomidou.mybatisplus.extension.plugins.pagination.Page<AdminCapsuleWallVO>> wallApproved(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(capsuleService.wallApproved(page, Math.min(size, 50)));
    }

    @PostMapping("/wall/{id}/decide")
    @Operation(summary = "胶囊墙审核", description = "R17：approve=true 上墙（1→2）/ false 拒绝（1→3，reason 必填）；"
            + "CAS 裁决，并发重复决定返回 409；裁决结果经通知服务告知作者")
    public ApiResponse<Void> decideWall(
            @Parameter(description = "胶囊 ID", required = true) @PathVariable("id") Long id,
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @RequestBody WallDecisionRequest request) {
        capsuleService.decideWall(id, request.approve(), request.reason(), adminUserId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/wall/{id}/take-down")
    @Operation(summary = "胶囊墙下架", description = "R17：管理员将已上墙内容下架（2→4，reason 必填）；"
            + "下架后公开墙不可见，理由与通知告知作者，作者可修改后重新申请")
    public ApiResponse<Void> takeDownWall(
            @Parameter(description = "胶囊 ID", required = true) @PathVariable("id") Long id,
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @RequestBody WallTakeDownRequest request) {
        capsuleService.adminTakeDown(id, request.reason(), adminUserId);
        return ApiResponse.ok(null);
    }
}
