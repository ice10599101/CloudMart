package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.ChallengeBattleRequest;
import com.cloudmart.pet.service.PetBattleService;
import com.cloudmart.pet.vo.PetBattleVO;
import com.cloudmart.pet.vo.PetOpponentVO;
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
 * 宠物对战接口（异步回合制，计算全部在服务端——原文档 §13；Cocos 只播放回合流水）。
 */
@RestController
@RequestMapping("/battle")
@Tag(name = "宠物对战", description = "候选对手、发起挑战、应战/拒绝、详情与历史")
@RequiredArgsConstructor
public class PetBattleController {

    private final PetBattleService battleService;

    @GetMapping("/opponents")
    @Operation(summary = "对战候选", description = "PvE 野生宠物 3 只 + PvP 其他用户公开宠物（等级±5优先，随机8只）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetOpponentVO>> opponents(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(battleService.listOpponents(userId));
    }

    @PostMapping("/challenge")
    @Operation(summary = "发起挑战", description = "PvE 立即结算返回回合流水；PvP 快照双方属性落 PENDING 并通知防守方")
    @SentinelResource("PET_BATTLE")
    public ApiResponse<PetBattleVO> challenge(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody ChallengeBattleRequest request) {
        return ApiResponse.ok(battleService.challenge(userId, request));
    }

    @PostMapping("/{battleId}/accept")
    @Operation(summary = "接受挑战", description = "防守方专用；按快照+seed 计算 → FINISHED，双方发奖；重复处理 409")
    @SentinelResource("PET_BATTLE")
    public ApiResponse<PetBattleVO> accept(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("battleId") Long battleId) {
        return ApiResponse.ok(battleService.accept(userId, battleId));
    }

    @PostMapping("/{battleId}/decline")
    @Operation(summary = "拒绝挑战", description = "防守方专用；PENDING → DECLINED 并通知挑战方")
    @SentinelResource("PET_BATTLE")
    public ApiResponse<PetBattleVO> decline(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("battleId") Long battleId) {
        return ApiResponse.ok(battleService.decline(userId, battleId));
    }

    @GetMapping("/{battleId}")
    @Operation(summary = "对战详情", description = "仅双方参与者可见（403）；含回合流水 JSON")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetBattleVO> detail(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("battleId") Long battleId) {
        return ApiResponse.ok(battleService.get(userId, battleId));
    }

    @GetMapping("/history")
    @Operation(summary = "对战历史", description = "我的对战（攻/守双侧），offset 分页")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetBattleVO>> history(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.ok(battleService.history(userId, page, pageSize));
    }
}
