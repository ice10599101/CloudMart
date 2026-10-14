package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetEvolutionService;
import com.cloudmart.pet.vo.PetEvolutionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物进化接口（原文档 §89 宠物进化：等级门槛 + 星光消耗 → 属性提升/皮肤解锁）。
 */
@RestController
@RequestMapping
@Tag(name = "宠物进化", description = "进化状态、执行进化")
@RequiredArgsConstructor
public class PetEvolutionController {

    private final PetEvolutionService evolutionService;

    @GetMapping("/evolution")
    @Operation(summary = "进化状态", description = "当前阶段 + 下一阶条件（等级/星光/属性提升/解锁皮肤）+ 是否可进化")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetEvolutionVO> status(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(evolutionService.status(userId));
    }

    /**
     * R28：必需幂等键（缺键 400 PET_REQUEST_KEY_INVALID）；C17 起请求体与
     * expectedFromStage 必填——阶段 CAS 由客户端显式确认，删除无 body 兼容旁路。
     * 同键重放返回原进化结果（不推进下一阶段）；同键异参 409 PET_IDEMPOTENCY_CONFLICT。
     */
    @PostMapping("/evolution/evolve")
    @Operation(summary = "执行进化（R28 意图冻结；C17 强制显式阶段）", description = "必需幂等键与请求体；"
            + "expectedFromStage 必填（与当前阶段不符 409 PET_STATE_CONFLICT）；等级不足 409 PET_LEVEL_REQUIRED；"
            + "已满阶 409 PET_EVOLUTION_MAX；星光不足 402；同键重试返回原结果，不会重复扣款或连续进化")
    @SentinelResource("PET_EVOLUTION")
    public ApiResponse<PetEvolutionVO> evolve(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody EvolveRequest request) {
        return ApiResponse.ok(evolutionService.evolve(userId, request.petId(), request.expectedFromStage()));
    }

    /** R28/C17 进化请求：petId 可空（首次执行绑定当前主宠并冻结）；expectedFromStage 必填 */
    public record EvolveRequest(
            Long petId,
            @NotNull(message = "请确认当前进化阶段后再进化") Integer expectedFromStage) {
    }
}
