package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.ApplyCareerRequest;
import com.cloudmart.pet.service.PetCareerService;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetCareerItemVO;
import com.cloudmart.pet.vo.PetCareerVO;
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

/**
 * 宠物职业接口（三期）。
 *
 * <p>职业是长期工作：入职 → 反复完成职业工作（复用统一活动状态机）→ 晋升。
 * 数值与资格全部由服务端判定，客户端只发意图。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物职业", description = "职业列表/入职/职业工作/晋升")
@RequiredArgsConstructor
public class PetCareerController {

    private final PetCareerService careerService;

    @GetMapping("/career")
    @Operation(summary = "职业面板", description = "当前职业 + 全部职业（含锁定原因）+ 工作历史 + 进行中的职业工作")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetCareerVO> career(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(careerService.status(userId));
    }

    @PostMapping("/career/apply")
    @Operation(summary = "入职/转职", description = "等级/智力不满足 409 PET_CAREER_LOCKED；高阶职业需先晋升")
    @SentinelResource("PET_CAREER")
    public ApiResponse<PetCareerItemVO> apply(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody ApplyCareerRequest request) {
        return ApiResponse.ok(careerService.apply(userId, request));
    }

    @PostMapping("/career/work/start")
    @Operation(summary = "开始职业工作", description = "与打工共用「一次只能做一件事」；每日次数上限见配置")
    @SentinelResource("PET_ACTIVITY_START")
    public ApiResponse<PetActivityVO> startWork(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(careerService.startWork(userId));
    }

    @PostMapping("/career/work/claim")
    @Operation(summary = "领取职业工作奖励", description = "CAS 幂等；累计工作次数；经验本地 + 星光 Feign（失败整体回滚）")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetActivityVO> claimWork(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(careerService.claimWork(userId));
    }

    @PostMapping("/career/promote")
    @Operation(summary = "晋升", description = "条件：工作次数 + 目标职业等级 + 星光；最高阶 409 PET_CAREER_MAX_TIER")
    @SentinelResource("PET_CAREER")
    public ApiResponse<PetCareerItemVO> promote(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(careerService.promote(userId));
    }
}
