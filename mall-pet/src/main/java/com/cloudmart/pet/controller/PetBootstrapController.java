package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.impl.PetBootstrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 轻量聚合读端（§7.2）：启动快照与活动中心摘要——明细列表走各既有端点，
 * 本端点只做轻量聚合（不调 AI/远程文件/全量流水）。
 */
@RestController
@Tag(name = "宠物聚合读端", description = "启动快照 bootstrap、活动中心摘要 activity-center")
@RequiredArgsConstructor
public class PetBootstrapController {

    private final PetBootstrapService bootstrapService;

    @GetMapping("/bootstrap")
    @Operation(summary = "启动聚合快照（§7.2）", description = "serverNow/businessDate/nextResetAt/activePetId/"
            + "pets 摘要/选定宠物权威快照+version/walletSummary/capabilities/quotas/actionAvailability/"
            + "pendingOperations；无宠物返回空 pets 不抛错；非本人 petId 拒绝")
    @SentinelResource("PET_QUERY")
    public ApiResponse<Map<String, Object>> bootstrap(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "选定宠物 ID（缺省取主宠）") @RequestParam(value = "petId", required = false) Long petId) {
        return ApiResponse.ok(bootstrapService.bootstrap(userId, petId));
    }

    @GetMapping("/activity-center")
    @Operation(summary = "活动中心摘要（§7.2）", description = "serverNow/accountBusyActivity/selectedPetActivity/"
            + "pendingClaimsCount/dailySetSummary/eventSummary/cooperationSummary；明细按需加载既有端点")
    @SentinelResource("PET_QUERY")
    public ApiResponse<Map<String, Object>> activityCenter(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "宠物 ID（缺省取主宠）") @RequestParam(value = "petId", required = false) Long petId) {
        return ApiResponse.ok(bootstrapService.activityCenter(userId, petId));
    }
}
