package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetInteractionService;
import com.cloudmart.pet.vo.PetActionVO;
import java.util.List;
import com.cloudmart.pet.vo.PetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物基础互动：喂食/玩耍/清洁/休息。
 * 客户端（Cocos 场景/宿主）只发意图——数值、经验、限频全部服务端结算（原文档 §8/§10）。
 */
@RestController
@RequestMapping
@Tag(name = "宠物互动", description = "喂食、玩耍、清洁、休息")
@RequiredArgsConstructor
public class PetInteractionController {

    private final PetInteractionService interactionService;

    @PostMapping("/feed")
    @Operation(summary = "喂食", description = "饥饿+30/心情+5/HP+10/经验+2；每日 5 次（429 PET_INTERACTION_RATE_LIMITED）；饱食已满 409")
    @SentinelResource("PET_INTERACTION")
    public ApiResponse<PetVO> feed(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(interactionService.feed(userId));
    }

    @PostMapping("/play")
    @Operation(summary = "玩耍", description = "精力-15/心情+20/经验+8；精力不足 409")
    @SentinelResource("PET_INTERACTION")
    public ApiResponse<PetVO> play(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(interactionService.play(userId));
    }

    @PostMapping("/clean")
    @Operation(summary = "清洁", description = "清洁度+40/心情+5/经验+2；清洁度已高 409")
    @SentinelResource("PET_INTERACTION")
    public ApiResponse<PetVO> clean(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(interactionService.clean(userId));
    }

    @PostMapping("/rest")
    @Operation(summary = "休息", description = "精力回满/HP 回满/饥饿-5；打工/读书/捞瓶进行中 409")
    @SentinelResource("PET_INTERACTION")
    public ApiResponse<PetVO> rest(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(interactionService.rest(userId));
    }

    @GetMapping("/pet/pets/{petId}/actions")
    @Operation(summary = "动作可执行性查询（B06）", description = "每个动作的 allowed/reasonCode/reasonText/"
            + "nextAvailableAt/rewardRemainingToday；客户端按钮禁用与文案依据，服务端权威")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetActionVO>> actions(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(interactionService.actions(userId));
    }
}
