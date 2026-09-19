package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetRankingService;
import com.cloudmart.pet.service.PetShareService;
import com.cloudmart.pet.vo.PetRankingVO;
import com.cloudmart.pet.vo.PetShareCardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 宠物社区融合接口（原文档 §80：宠物排行榜 + 宠物动态/分享）。
 */
@RestController
@RequestMapping
@Tag(name = "宠物排行与分享", description = "宠物排行榜、宠物动态分享卡片")
@RequiredArgsConstructor
public class PetCommunityController {

    private final PetRankingService rankingService;
    private final PetShareService shareService;

    @GetMapping("/rankings")
    @Operation(summary = "宠物排行榜", description = "三榜（LEVEL 等级/BATTLE_WIN 胜场/BOTTLE 捞瓶）Top 20 + 我的数值与名次；"
            + "仅公开宠物入榜（原文档 §80）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetRankingService.PetRankingResult> rankings(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "榜单维度: LEVEL/BATTLE_WIN/BOTTLE")
            @RequestParam(value = "type", defaultValue = "LEVEL") String type) {
        PetRankingService.RankingType rankingType;
        try {
            rankingType = PetRankingService.RankingType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            rankingType = PetRankingService.RankingType.LEVEL;
        }
        return ApiResponse.ok(rankingService.ranking(rankingType, userId));
    }

    @GetMapping("/share/card")
    @Operation(summary = "宠物动态分享卡片", description = "文案由服务端生成（LEVEL_UP 成长/ACHIEVEMENT 成就/BOTTLE 捞瓶/BATTLE 对战/DAILY 日常），"
            + "前端复制后跳转发帖页（原文档 §36：用户可发布到社区，不自动发帖）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetShareCardVO> shareCard(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "卡片类型: LEVEL_UP/ACHIEVEMENT/BOTTLE/BATTLE/DAILY")
            @RequestParam(value = "type", defaultValue = "DAILY") String type) {
        return ApiResponse.ok(shareService.buildCard(userId, type));
    }
}
