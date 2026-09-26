package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.StartStudyRequest;
import com.cloudmart.pet.dto.StartWorkRequest;
import com.cloudmart.pet.service.PetActivityService;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetJobVO;
import com.cloudmart.pet.vo.PetStudyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 打工/读书接口（统一 pet_activity 状态机：开工→倒计时→领取，原文档 §11/§12/§74）。
 */
@RestController
@RequestMapping
@Tag(name = "打工与读书", description = "岗位/课程列表、开工、按任务 ID 领取奖励、统一活动列表")
@RequiredArgsConstructor
public class PetActivityController {

    private final PetActivityService activityService;

    @GetMapping("/jobs")
    @Operation(summary = "打工岗位列表", description = "配置服务端下发（前端禁止硬编码数值），含当前宠物可接单标记")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetJobVO>> jobs(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(activityService.listJobs(userId));
    }

    @PostMapping("/work/start")
    @Operation(summary = "开始打工", description = "互斥校验→等级/精力/饥饿校验→冻结规则快照→立即扣消耗")
    @SentinelResource("PET_ACTIVITY_START")
    public ApiResponse<PetActivityVO> startWork(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody StartWorkRequest request) {
        return ApiResponse.ok(activityService.startWork(userId, request));
    }

    @PostMapping("/work/claim")
    @Operation(summary = "领取打工奖励（兼容入口）",
            description = "稳定取本人最新一条可领取 WORK；星光经统一操作记录幂等发放，结果未知返回结算中")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetActivityVO> claimWork(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(activityService.claimWork(userId));
    }

    @GetMapping("/activities")
    @Operation(summary = "统一活动列表（B09）",
            description = "进行中/待领取/已领取/已过期全部可见，支持 status/petId 过滤与分页；含领取截止时间")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetActivityVO>> activities(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "状态过滤 IN_PROGRESS/COMPLETED/CLAIMED/EXPIRED") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "宠物过滤（奖励归属宠物）") @RequestParam(value = "petId", required = false) Long petId,
            @Parameter(description = "页码，从 1 起") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "页大小，默认 20 最大 50") @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(activityService.listActivities(userId, status, petId, page, size));
    }

    @PostMapping("/activities/{activityId}/claim")
    @Operation(summary = "按活动 ID 领取奖励（B03/B09）",
            description = "唯一任务归属：校验活动属于当前用户，奖励归 activity.petId（开工宠物，与当前主宠无关）；"
                    + "支持 WORK/STUDY/CAREER_WORK/BOTTLE_FISHING")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetActivityVO> claimActivity(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "活动 ID") @PathVariable("activityId") Long activityId) {
        return ApiResponse.ok(activityService.claimActivity(userId, activityId));
    }

    @GetMapping("/studies")
    @Operation(summary = "读书课程列表", description = "配置服务端下发，含当前宠物可选课标记")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetStudyVO>> studies(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(activityService.listStudies(userId));
    }

    @PostMapping("/study/start")
    @Operation(summary = "开始读书", description = "同打工互斥语义")
    @SentinelResource("PET_ACTIVITY_START")
    public ApiResponse<PetActivityVO> startStudy(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody StartStudyRequest request) {
        return ApiResponse.ok(activityService.startStudy(userId, request));
    }

    @PostMapping("/study/claim")
    @Operation(summary = "领取读书奖励（兼容入口）", description = "稳定取本人最新一条可领取 STUDY：经验+智力")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetActivityVO> claimStudy(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(activityService.claimStudy(userId));
    }
}
