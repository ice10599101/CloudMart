package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.entity.PetQuestEventReceipt;
import com.cloudmart.pet.service.PetDailyQuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端任务事件回执（R32/§8.2）：收据可查、SKIPPED_STALE 可重放——
 * 只允许重放已有事实，不提供手工改进度的入口。
 */
@RestController
@RequestMapping("/admin/pet/quests")
@Tag(name = "宠物管理·任务回执", description = "任务事件回执查询与补算重放（管理员）")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
public class AdminPetQuestController {

    private final PetDailyQuestService questService;

    @GetMapping("/receipts")
    @Operation(summary = "收据查询", description = "按用户/任务类型/状态过滤 + 分页；"
            + "status: APPLIED 已计入 / SKIPPED_STALE 事实过期待补算")
    public ApiResponse<Page<PetQuestEventReceipt>> receipts(
            @Parameter(description = "用户 ID") @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "任务类型（WORK/CHAT/COMPANION...）") @RequestParam(value = "questCode", required = false) String questCode,
            @Parameter(description = "状态过滤") @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        List<PetQuestEventReceipt> rows = questService.receipts(userId, questCode, status, page, size);
        Page<PetQuestEventReceipt> result = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 50));
        result.setRecords(rows);
        return ApiResponse.ok(result);
    }

    @PostMapping("/receipts/{id}/replay")
    @Operation(summary = "重放收据（补算）", description = "仅 SKIPPED_STALE 可重放：按事实归属业务日补记进度；"
            + "该日任务行不存在时拒绝（不允许为历史日凭空生成）；CAS 防并发双放")
    public ApiResponse<PetQuestEventReceipt> replay(
            @Parameter(description = "回执 ID") @PathVariable("id") Long id) {
        return ApiResponse.ok(questService.replayReceipt(id));
    }

    public record QuestCancelRequest(String reason) {
    }

    @PostMapping("/instances/{petId}/{questDate}/{questCode}/cancel")
    @Operation(summary = "取消某日任务实例（§8.2 受审计命令）", description = "IN_PROGRESS/COMPLETE 可取消；"
            + "CLAIMED 拒绝（奖励已发出，收回走调账补偿链路）；reason 必填随行落库")
    public ApiResponse<com.cloudmart.pet.entity.PetDailyQuest> cancelInstance(
            @Parameter(description = "宠物 ID") @PathVariable("petId") Long petId,
            @Parameter(description = "业务日（ISO 串）") @PathVariable("questDate") String questDate,
            @Parameter(description = "任务 code") @PathVariable("questCode") String questCode,
            @org.springframework.web.bind.annotation.RequestBody QuestCancelRequest request) {
        // 操作者取服务令牌声明（P0-3：不信任可伪造请求头）
        return ApiResponse.ok(questService.cancelQuestInstance(petId,
                java.time.LocalDate.parse(questDate), questCode,
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator(), request.reason()));
    }
}
