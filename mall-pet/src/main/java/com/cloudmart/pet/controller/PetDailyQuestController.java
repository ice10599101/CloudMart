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
 * <p>任务按用户业务日（R22 用户时区，PetClock 零点）自然重置：列表入口惰性生成当日任务行，
 * 进度由玩法埋点累加。C14 起领奖唯一路径为任务集实例路由（setId/questId 均为实体 ID），
 * 旧的按 code/当日隐式解析入口与日期串回退已物理删除。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物每日任务", description = "今日任务列表/按任务集领奖/全清宝箱")
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
    @Operation(summary = "今日任务", description = "任务列表 + 进度 + 全清宝箱状态（首次访问自动生成当日任务）；"
            + "返回携带当日任务集 ID，领奖走 /daily-quest-sets/{setId} 实例路由（C14）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetDailyQuestVO> dailyQuests(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(dailyQuestService.list(userId));
    }

    // ---------------- 任务集路由（R32 §7.2；C14 唯一领奖路径：setId/questId 均为实体 ID） ----------------

    @PostMapping("/daily-quest-sets/{setId}/quests/{questId}/claim")
    @Operation(summary = "按任务集领取单项（R32/C14）", description = "setId=任务集实体 ID、questId=任务实体 ID；"
            + "归属+时间校验后幂等 CAS 领取，非数字 ID 返回 400")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetDailyQuestItemVO> claimInSet(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("setId") Long setId,
            @PathVariable("questId") Long questId) {
        return ApiResponse.ok(dailyQuestService.claimInSet(userId, setId, questId));
    }

    @PostMapping("/daily-quest-sets/{setId}/claim-all")
    @Operation(summary = "按任务集一键领取（R32/C14）", description = "逐项独立 CAS 与幂等；"
            + "含最后宝箱独立评估，逐项返回终态")
    @SentinelResource("PET_QUEST_CLAIM")
    public ApiResponse<com.cloudmart.pet.vo.ClaimAllResult> claimAllInSet(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("setId") Long setId) {
        return ApiResponse.ok(dailyQuestService.claimAllInSet(userId, setId));
    }

    @PostMapping("/daily-quest-sets/{setId}/chest/claim")
    @Operation(summary = "按任务集领取宝箱（R32/C14）", description = "绑定任务集与宠物的同一 CAS 幂等语义")
    @SentinelResource("PET_ACTIVITY_CLAIM")
    public ApiResponse<PetDailyQuestVO> claimChestInSet(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("setId") Long setId) {
        return ApiResponse.ok(dailyQuestService.claimChestInSet(userId, setId));
    }
}
