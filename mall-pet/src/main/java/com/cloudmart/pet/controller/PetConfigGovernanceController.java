package com.cloudmart.pet.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.entity.PetConfigVersion;
import com.cloudmart.pet.service.impl.PetConfigGovernanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * B21 配置治理：校验预览 / 历史版本 / 回退（管理员审计链路）。
 */
@RestController
@RequestMapping("/admin/pet/config-governance")
@Tag(name = "宠物管理·配置治理", description = "数值校验预览、历史版本、回退（B21）")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
public class PetConfigGovernanceController {

    private final PetConfigGovernanceService governanceService;

    public record ValidateRequest(String configType, Map<String, Object> data) {
    }

    @PostMapping("/validate")
    @Operation(summary = "校验预览（B21）", description = "数值上下限组合校验，不落库")
    public ApiResponse<Void> validate(@RequestBody ValidateRequest request) {
        if (request.configType() == null || request.configType().isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "configType 必填");
        }
        governanceService.validate(request.configType(), request.data());
        return ApiResponse.ok(null);
    }

    @GetMapping("/history")
    @Operation(summary = "配置历史版本（B21）", description = "按类型+配置 ID 查询发布/回退历史（最近 50 条）")
    public ApiResponse<List<PetConfigVersion>> history(
            @RequestParam("configType") String configType,
            @RequestParam("configId") Long configId) {
        return ApiResponse.ok(governanceService.history(configType, configId));
    }

    public record RollbackRequest(String configType, Long configId, Integer version, String operator) {
    }

    @PostMapping("/rollback")
    @Operation(summary = "回退配置（B21）", description = "将指定版本快照写回目标行；回退动作本身留版本审计")
    public ApiResponse<Void> rollback(@RequestBody RollbackRequest request) {
        if (request.configType() == null || request.configId() == null || request.version() == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "回退参数不完整");
        }
        governanceService.rollback(request.configType(), request.configId(), request.version(), request.operator());
        return ApiResponse.ok(null);
    }
}
