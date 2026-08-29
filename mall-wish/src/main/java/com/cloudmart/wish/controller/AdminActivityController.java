package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.ActivityRewardLog;
import com.cloudmart.wish.entity.CommunityActivity;
import com.cloudmart.wish.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台社区活动 Controller（Sprint 3.5 管理后台：活动 CRUD +
 * condition JSON 编辑 + 状态机控制 + 奖励发放日志审计）。
 *
 * <p>路由前缀 /admin/activity，仅内部服务调用；权限点
 * {@code business:activity:list/add/edit/delete/reward}。活动创建需管理员
 * 权限（安全验收）。</p>
 */
@RestController
@RequestMapping("/admin/activity")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-社区活动", description = "活动 CRUD/状态机/奖励发放（Sprint 3.5）")
@RequiredArgsConstructor
public class AdminActivityController {

    private final ActivityService activityService;

    @GetMapping("/list")
    @Operation(summary = "活动列表（全状态）", description = "status/type 过滤可选，分页")
    public ApiResponse<List<CommunityActivity>> listForAdmin(
            @Parameter(description = "状态过滤") @RequestParam(required = false) String status,
            @Parameter(description = "类型过滤") @RequestParam(required = false) String type,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.ok(activityService.listForAdmin(status, type, page, size));
    }

    @PostMapping
    @Operation(summary = "创建活动", description = "初始 DRAFT；condition JSON 校验（非法 400）")
    public ApiResponse<CommunityActivity> create(
            @RequestBody CommunityActivity activity,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(activityService.create(activity, adminUserId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新活动", description = "仅 DRAFT/ACTIVE 可改")
    public ApiResponse<CommunityActivity> update(
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id,
            @RequestBody CommunityActivity activity,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(activityService.update(id, activity, adminUserId));
    }

    @PostMapping("/{id}/transition")
    @Operation(summary = "状态机流转", description = "action=start（DRAFT→ACTIVE）/end（ACTIVE→ENDED）/"
            + "archive（ENDED→ARCHIVED）；非法流转 409")
    public ApiResponse<CommunityActivity> transition(
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id,
            @RequestParam String action,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(activityService.transition(id, action, adminUserId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除活动", description = "仅 DRAFT 可删除")
    public ApiResponse<Void> delete(
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id) {
        activityService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/rewards")
    @Operation(summary = "发放奖励", description = "条件达成后对全部参与/组队用户发放星光/徽章；"
            + "uk 幂等（重复发放跳过计入 skipped）；日志可审计")
    public ApiResponse<ActivityService.RewardIssueStats> issueRewards(
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(activityService.issueRewards(id, adminUserId));
    }

    @GetMapping("/{id}/rewards/logs")
    @Operation(summary = "奖励发放日志", description = "审计：时间/用户/奖励类型/数量（活动维度倒序）")
    public ApiResponse<List<ActivityRewardLog>> listRewardLogs(
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id) {
        return ApiResponse.ok(activityService.listRewardLogs(id));
    }
}
