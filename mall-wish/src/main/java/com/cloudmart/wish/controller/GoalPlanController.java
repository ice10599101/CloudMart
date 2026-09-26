package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.WishAiGoal;
import com.cloudmart.wish.enums.GoalStatus;
import com.cloudmart.wish.service.impl.GoalPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 心愿目标计划（N04，/api/wish/v2/**）：用户可直接建/编辑/勾选步骤（AI 仅草案）；
 * 全部作者权限 + version CAS；步骤完成与心愿还愿是两件事，不因勾选发奖励。
 */
@RestController
@RequestMapping("/v2")
@Tag(name = "心愿宇宙·目标计划", description = "AI 目标计划化（N04）")
@RequiredArgsConstructor
public class GoalPlanController {

    private final GoalPlanService goalPlanService;

    public record CreateGoalRequest(String title, String description, Integer estimatedDays,
                                    Integer priority, Integer sortOrder) {
    }

    public record UpdateGoalRequest(String title, String description, GoalStatus status, Long version) {
    }

    @GetMapping("/wishes/{id}/goals")
    @Operation(summary = "目标清单", description = "作者专用；按 sortOrder 排序")
    public ApiResponse<List<WishAiGoal>> list(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long wishId) {
        return ApiResponse.ok(goalPlanService.listGoals(userId, wishId));
    }

    @PostMapping("/wishes/{id}/goals")
    @Operation(summary = "创建步骤", description = "用户可直接创建（AI 生成失败不阻塞）；每心愿最多 20 步")
    public ApiResponse<WishAiGoal> create(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long wishId,
            @RequestBody CreateGoalRequest request) {
        return ApiResponse.ok(goalPlanService.createGoal(userId, wishId, request.title(),
                request.description(), request.estimatedDays(), request.priority(), request.sortOrder()));
    }

    @PatchMapping("/goals/{id}")
    @Operation(summary = "编辑/勾选步骤", description = "version CAS；勾选完成不触发还愿奖励")
    public ApiResponse<WishAiGoal> update(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long goalId,
            @RequestBody UpdateGoalRequest request) {
        return ApiResponse.ok(goalPlanService.updateGoal(userId, goalId, request.title(),
                request.description(), request.status(), request.version()));
    }

    @DeleteMapping("/goals/{id}")
    @Operation(summary = "删除步骤", description = "软删；version CAS；删除已完成步骤需客户端先出重算预览")
    public ApiResponse<Void> delete(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long goalId,
            @RequestParam(required = false) Long version) {
        goalPlanService.deleteGoal(userId, goalId, version);
        return ApiResponse.ok(null);
    }

    @PutMapping("/wishes/{id}/goal-order")
    @Operation(summary = "批量排序", description = "goalOrder={goalId:sortOrder}；集合必须完整且同心愿")
    public ApiResponse<Void> reorder(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long wishId,
            @RequestBody Map<Long, Integer> goalOrder) {
        goalPlanService.reorder(userId, wishId, goalOrder);
        return ApiResponse.ok(null);
    }
}
