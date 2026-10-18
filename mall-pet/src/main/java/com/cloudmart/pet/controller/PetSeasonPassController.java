package com.cloudmart.pet.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetSeasonPassService;
import com.cloudmart.pet.vo.PetSeasonPassVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 赛季通行证（§6/R05）：经验事实累积 + 逐档权威领取 + 可恢复交付。
 */
@RestController
@RequestMapping("/season/pass")
@RequiredArgsConstructor
@Tag(name = "赛季通行证", description = "R05：逐档领取权威裁决（并发互不覆盖）、交付失败可补发")
public class PetSeasonPassController {

    private final PetSeasonPassService passService;

    @GetMapping
    @Operation(summary = "我的通行证", description = "当前赛季 + 经验 + 档位阶梯（含领取状态；CLAIM_PENDING 不计为已领取）")
    public ApiResponse<PetSeasonPassVO> myPass(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(passService.myPass(userId));
    }

    @GetMapping("/seasons")
    @Operation(summary = "我参与过的赛季列表（T19）", description = "含当前与历史赛季，按结束时间倒序——前端赛季选择器数据源；仅返回有通行证记录的赛季")
    public ApiResponse<java.util.List<com.cloudmart.pet.entity.PetSeason>> mySeasons(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(passService.mySeasons(userId));
    }

    @GetMapping("/{seasonId}")
    @Operation(summary = "按赛季查询通行证", description = "R05：已结束赛季在领奖截止前可查可领取项")
    public ApiResponse<PetSeasonPassVO> seasonPass(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable Long seasonId) {
        return ApiResponse.ok(passService.seasonPass(userId, seasonId));
    }

    @PostMapping("/claim")
    @Operation(summary = "领取档位奖励", description = "R05：uk(season,user,tier) 权威裁决——并发领不同档互不覆盖、"
            + "同档重复返回原状态；币/皮肤交付失败留 CLAIM_PENDING 待补发，不虚报已领取")
    public ApiResponse<PetSeasonPassVO> claim(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(value = "seasonId", required = false) Long seasonId,
            @RequestParam("tier") int tier,
            @RequestParam(value = "requestKey", required = false) String requestKey) {
        return ApiResponse.ok(passService.claimTier(userId, seasonId, tier, requestKey));
    }
}
