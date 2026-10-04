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
    public ApiResponse<com.cloudmart.pet.vo.ClaimAllResult> claimAll(
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

    // ---------------- 任务集路由（R32 §7.2：与按 code/当日路径委托同一服务） ----------------

    /**
     * setId 即任务集实例（当日 businessDate 的 ISO 串）：归属+时间校验后委托同一服务，
     * 请求键与 setId 绑定由网关/客户端 Idempotency-Key 语义承接；过期集明确拒绝。
     */
    private void requireSetMatchesToday(Long userId, String setId) {
        java.time.LocalDate questDate = dailyQuestService.list(userId).questDate();
        if (questDate == null || !questDate.toString().equals(setId)) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR,
                    "任务集不存在或已过期（setId 需为当日 questDate）");
        }
    }

    @PostMapping("/daily-quest-sets/{setId}/quests/{questId}/claim")
    @Operation(summary = "按任务集领取单项（R32）", description = "setId=当日 questDate（ISO 串）；"
            + "归属+时间校验后与 /daily-quests/{code}/claim 同一服务、同一幂等语义")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetDailyQuestItemVO> claimInSet(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("setId") String setId,
            @PathVariable("questId") String questId) {
        requireSetMatchesToday(userId, setId);
        return ApiResponse.ok(dailyQuestService.claim(userId, questId));
    }

    @PostMapping("/daily-quest-sets/{setId}/claim-all")
    @Operation(summary = "按任务集一键领取（R32）", description = "与 /daily-quests/claim-all 同一服务；"
            + "含最后宝箱独立评估，逐项返回终态")
    @SentinelResource("PET_QUEST_CLAIM")
    public ApiResponse<com.cloudmart.pet.vo.ClaimAllResult> claimAllInSet(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("setId") String setId) {
        requireSetMatchesToday(userId, setId);
        return ApiResponse.ok(dailyQuestService.claimAll(userId));
    }

    @PostMapping("/daily-quest-sets/{setId}/chest/claim")
    @Operation(summary = "按任务集领取宝箱（R32）", description = "与 /daily-quests/chest/claim 同一服务、同一 CAS 幂等")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetDailyQuestVO> claimChestInSet(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("setId") String setId) {
        requireSetMatchesToday(userId, setId);
        return ApiResponse.ok(dailyQuestService.claimChest(userId));
    }
}
