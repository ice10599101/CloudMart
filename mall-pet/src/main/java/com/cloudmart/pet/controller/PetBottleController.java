package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetBottleFishingService;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetBottleStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物捞漂流瓶接口（复用 mall-wish 漂流瓶池——原文档 §14-20）。
 */
@RestController
@RequestMapping
@Tag(name = "宠物捞漂流瓶", description = "捞瓶状态、开始捞瓶、领取结果")
@RequiredArgsConstructor
public class PetBottleController {

    private final PetBottleFishingService bottleFishingService;

    @GetMapping("/bottle/status")
    @Operation(summary = "捞瓶状态", description = "任务剩余时间/可领取/冷却/按属性估算的成功率/解锁区域；查询即触发到期惰性结算")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetBottleStatusVO> status(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(bottleFishingService.status(userId));
    }

    @PostMapping("/bottle/start")
    @Operation(summary = "开始捞瓶", description = "30 分钟任务（服务端时间判定，关闭 App 也不影响）；冷却 10 分钟；精力 ≥10")
    @SentinelResource("PET_BOTTLE")
    public ApiResponse<PetActivityVO> start(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(bottleFishingService.start(userId));
    }

    @PostMapping("/bottle/claim")
    @Operation(summary = "领取捞瓶结果", description = "CAS 幂等；result.outcome: CAUGHT(含bottleId)/EMPTY/FAILED(可重试)；"
            + "CAUGHT 后跳转漂流瓶页查看真实瓶子")
    @SentinelResource("PET_BOTTLE")
    public ApiResponse<PetActivityVO> claim(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(bottleFishingService.claim(userId));
    }
}
