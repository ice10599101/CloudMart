package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.vo.PetDailyQuestItemVO;
import com.cloudmart.pet.vo.PetDailyQuestVO;
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
 * 宠物每日任务接口（三期）。
 *
 * <p>任务每天 0 点（UTC）自然重置：列表入口惰性生成当日任务行，
 * 进度由玩法埋点累加，领奖与全清宝箱都是 CAS 幂等。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物每日任务", description = "今日任务列表/领奖/全清宝箱")
@RequiredArgsConstructor
public class PetDailyQuestController {

    private final PetDailyQuestService dailyQuestService;

    @GetMapping("/daily-quests")
    @Operation(summary = "今日任务", description = "任务列表 + 进度 + 全清宝箱状态（首次访问自动生成当日任务）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetDailyQuestVO> dailyQuests(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(dailyQuestService.list(userId));
    }

    @PostMapping("/daily-quests/{code}/claim")
    @Operation(summary = "领取任务奖励", description = "未完成 409 PET_QUEST_NOT_FINISHED；重复领取 409 PET_QUEST_ALREADY_CLAIMED")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetDailyQuestItemVO> claim(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("code") String code) {
        return ApiResponse.ok(dailyQuestService.claim(userId, code));
    }

    @PostMapping("/daily-quests/claim-all")
    @Operation(summary = "批量领取全部已完成项（B15）", description = "逐项独立 CAS 与幂等，单项失败跳过可重试")
    @SentinelResource("PET_QUEST_CLAIM")
    public ApiResponse<java.util.List<PetDailyQuestItemVO>> claimAll(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(dailyQuestService.claimAll(userId));
    }

    @PostMapping("/daily-quests/chest/claim")
    @Operation(summary = "领取全清宝箱", description = "有未领取任务时 409 PET_QUEST_CHEST_NOT_READY；已领 409 PET_QUEST_CHEST_CLAIMED")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetDailyQuestVO> claimChest(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(dailyQuestService.claimChest(userId));
    }
}
