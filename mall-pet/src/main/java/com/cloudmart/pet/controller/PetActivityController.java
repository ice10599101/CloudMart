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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 打工/读书接口（统一 pet_activity 状态机：开工→倒计时→领取，原文档 §11/§12/§74）。
 */
@RestController
@RequestMapping
@Tag(name = "打工与读书", description = "岗位/课程列表、开工、领取奖励")
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
    @Operation(summary = "开始打工", description = "互斥校验→等级/精力/饥饿校验→立即扣消耗；409 码见文档")
    @SentinelResource("PET_ACTIVITY_START")
    public ApiResponse<PetActivityVO> startWork(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody StartWorkRequest request) {
        return ApiResponse.ok(activityService.startWork(userId, request));
    }

    @PostMapping("/work/claim")
    @Operation(summary = "领取打工奖励", description = "CAS 幂等：重复领取 409；经验+星光（星光服务降级整体回滚可重试）")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetActivityVO> claimWork(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(activityService.claimWork(userId));
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
    @Operation(summary = "领取读书奖励", description = "CAS 幂等：经验+智力")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetActivityVO> claimStudy(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(activityService.claimStudy(userId));
    }
}
