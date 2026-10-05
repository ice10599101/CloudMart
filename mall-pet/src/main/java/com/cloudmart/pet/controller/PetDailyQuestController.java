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
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/daily-quest-sets")
    @Operation(summary = "任务集列表（PET-09）", description = "本人任务集：status=ACTIVE 当前与宽限期内 / EXPIRED 历史 / 空全部")
    public ApiResponse<java.util.List<com.cloudmart.pet.entity.PetDailyQuestSet>> questSets(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.ok(dailyQuestService.questSets(userId, status));
    }

    @GetMapping("/daily-quest-sets/{setId}")
    @Operation(summary = "任务集详情（PET-09）", description = "完整冻结任务/宝箱/领取截止；宽限期内与历史集可查（深链接/冲突恢复）")
    public ApiResponse<PetDailyQuestVO> questSetDetail(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("setId") Long setId) {
        return ApiResponse.ok(dailyQuestService.questSetDetail(userId, setId));
    }

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
    /**
     * PET-09：setId 解析——新客户端传真实任务集实体 ID；旧客户端传当日日期串时
     * 解析当前主宠当日集（兼容别名，退役期随 PET-23 收敛，不默默维持两套语义）。
     */
    private Long resolveSetId(Long userId, String setId) {
        try {
            return Long.parseLong(setId.strip());
        } catch (NumberFormatException legacyDateForm) {
            return dailyQuestService.currentSetId(userId);
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
        // PET-09：按集领取——归属校验（本人）、宽限截止与任务行归属（set_id 绑定）同事务；
        // questId 兼容任务实体 ID 与 code 两种形式
        return ApiResponse.ok(dailyQuestService.claimInSet(userId, resolveSetId(userId, setId), questId));
    }

    @PostMapping("/daily-quest-sets/{setId}/claim-all")
    @Operation(summary = "按任务集一键领取（R32）", description = "与 /daily-quests/claim-all 同一服务；"
            + "含最后宝箱独立评估，逐项返回终态")
    @SentinelResource("PET_QUEST_CLAIM")
    public ApiResponse<com.cloudmart.pet.vo.ClaimAllResult> claimAllInSet(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("setId") String setId) {
        // PET-09：批领绑定任务集（原实现只校验日期）；独立事务逐项领取语义不变
        return ApiResponse.ok(dailyQuestService.claimAllInSet(userId, resolveSetId(userId, setId)));
    }

    @PostMapping("/daily-quest-sets/{setId}/chest/claim")
    @Operation(summary = "按任务集领取宝箱（R32）", description = "与 /daily-quests/chest/claim 同一服务、同一 CAS 幂等")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetDailyQuestVO> claimChestInSet(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("setId") String setId) {
        // PET-09：宝箱领取绑定任务集（原实现只校验日期）
        return ApiResponse.ok(dailyQuestService.claimChestInSet(userId, resolveSetId(userId, setId)));
    }
}
