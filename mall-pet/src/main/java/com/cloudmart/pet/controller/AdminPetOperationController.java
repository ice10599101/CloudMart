package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.entity.PetOperation;
import com.cloudmart.pet.repository.PetOperationMapper;
import com.cloudmart.pet.service.impl.PetOperationRecoveryService;
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
 * 管理端交易操作查询/重试（B01/B21）：人工重试沿用同一 operationId（幂等），
 * 不会重复扣款/发薪；仅内部链路可达。
 */
@RestController
@RequestMapping("/admin/pet/operations")
@Tag(name = "宠物管理·交易操作", description = "待结算/失败操作查询与原单重试（管理员）")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
public class AdminPetOperationController {

    private final PetOperationMapper operationMapper;
    private final PetOperationRecoveryService recoveryService;

    @GetMapping
    @Operation(summary = "操作列表", description = "status 过滤（PENDING/UNKNOWN/FAILED/COMPENSATING/COMPENSATED/COMPLETED）+ 用户/宠物过滤 + 分页")
    public ApiResponse<List<PetOperation>> list(
            @Parameter(description = "状态过滤") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "用户过滤") @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "宠物过滤") @RequestParam(value = "petId", required = false) Long petId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        LambdaQueryWrapper<PetOperation> wrapper = new LambdaQueryWrapper<PetOperation>()
                .orderByDesc(PetOperation::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(PetOperation::getStatus, status.toUpperCase());
        }
        if (userId != null) {
            wrapper.eq(PetOperation::getUserId, userId);
        }
        if (petId != null) {
            wrapper.eq(PetOperation::getPetId, petId);
        }
        Page<PetOperation> result = operationMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(size, 50)), wrapper);
        return ApiResponse.ok(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @PostMapping("/{operationId}/retry")
    @Operation(summary = "原单重试（B01）", description = "复用同一 operationId 立即触发恢复（查询钱包/幂等重发）；不会产生第二次资金变动")
    public ApiResponse<Void> retry(
            @Parameter(description = "业务操作键") @PathVariable("operationId") String operationId) {
        PetOperation operation = operationMapper.selectOne(new LambdaQueryWrapper<PetOperation>()
                .eq(PetOperation::getOperationId, operationId));
        if (operation == null) {
            operationMapper.selectById(operationId);
        }
        if (operation == null) {
            com.cloudmart.common.exception.BusinessException e =
                    new com.cloudmart.common.exception.BusinessException(
                            com.cloudmart.pet.constant.PetErrorCodes.PET_OPERATION_NOT_FOUND, "操作不存在");
            throw e;
        }
        if ("COMPLETED".equals(operation.getStatus()) || "COMPENSATED".equals(operation.getStatus())) {
            // 已终态：幂等拒绝（不改变结果）
            return ApiResponse.ok(null);
        }
        recoveryService.retrySingle(operation);
        return ApiResponse.ok(null);
    }
}
