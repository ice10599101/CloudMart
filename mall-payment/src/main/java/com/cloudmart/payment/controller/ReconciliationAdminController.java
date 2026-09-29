package com.cloudmart.payment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.payment.reconciliation.ReconciliationDifference;
import com.cloudmart.payment.reconciliation.ReconciliationRun;
import com.cloudmart.payment.reconciliation.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OPS-01：对账工作台管理端点（mall-admin 代理，scope payment:admin）。
 * 人工解决不直接改资金，只登记证据/处置说明。
 */
@RestController
@RequestMapping("/admin/payments/reconciliation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "对账工作台", description = "OPS-01 对账运行与差异处置")
public class ReconciliationAdminController {

    private final com.cloudmart.payment.reconciliation.ReconciliationRunMapper runMapper;
    private final com.cloudmart.payment.reconciliation.ReconciliationDifferenceMapper differenceMapper;
    private final com.cloudmart.payment.reconciliation.ReconciliationService reconciliationService;

    @GetMapping("/runs")
    @Operation(summary = "对账运行分页", description = "按日期倒序；差异汇总随行；meta 携带 page/pageSize/total")
    public ApiResponse<List<ReconciliationRun>> listRuns(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<ReconciliationRun> result = runMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<ReconciliationRun>().orderByDesc(ReconciliationRun::getId));
        return ApiResponse.ok(result.getRecords(), page, Math.min(size, 100), result.getTotal());
    }

    @GetMapping("/runs/{runId}/differences")
    @Operation(summary = "差异查询", description = "按运行查差异；resolveStatus 过滤（OPEN 默认）；分页返回")
    public ApiResponse<List<ReconciliationDifference>> listDifferences(
            @Parameter(description = "对账运行ID", required = true) @PathVariable("runId") Long runId,
            @Parameter(description = "处置状态过滤") @RequestParam(value = "resolveStatus", required = false)
            String resolveStatus,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        var wrapper = new LambdaQueryWrapper<ReconciliationDifference>()
                .eq(ReconciliationDifference::getRunId, runId);
        if (resolveStatus != null && !resolveStatus.isBlank()) {
            wrapper.eq(ReconciliationDifference::getResolveStatus, resolveStatus);
        } else {
            wrapper.eq(ReconciliationDifference::getResolveStatus, "OPEN");
        }
        wrapper.orderByDesc(ReconciliationDifference::getSeverity)
                .orderByAsc(ReconciliationDifference::getId);
        Page<ReconciliationDifference> result = differenceMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), wrapper);
        return ApiResponse.ok(result.getRecords(), page, Math.min(Math.max(size, 1), 100), result.getTotal());
    }

    @PostMapping("/runs/execute")
    @Operation(summary = "执行一次对账", description = "scanDays：扫描最近 N 天的 SUCCESS 支付")
    public ApiResponse<ReconciliationRun> execute(
            @Parameter(description = "扫描天数") @RequestParam(value = "scanDays", defaultValue = "7")
            int scanDays) {
        return ApiResponse.ok(reconciliationService.runPaymentOrderReconciliation(scanDays));
    }

    public record ResolveRequest(
            @jakarta.validation.constraints.NotBlank String resolveStatus,
            @jakarta.validation.constraints.NotBlank String resolveNote) {
    }

    /** 人工处置：只登记证据/说明，不改资金；OPEN 才可处置（防覆盖已处置结论）。 */
    @PostMapping("/differences/{diffId}/resolve")
    @Operation(summary = "处置差异", description = "resolveStatus=RESOLVED/ACCEPTED + 处置说明；不直接改资金")
    public ApiResponse<Void> resolve(
            @Parameter(description = "差异ID", required = true) @PathVariable("diffId") Long diffId,
            @Parameter(description = "处置管理员（已验签令牌主体）", hidden = true)
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @Valid @RequestBody ResolveRequest request) {
        int updated = differenceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ReconciliationDifference>()
                .eq(ReconciliationDifference::getId, diffId)
                .eq(ReconciliationDifference::getResolveStatus, "OPEN")
                .set(ReconciliationDifference::getResolveStatus, request.resolveStatus())
                .set(ReconciliationDifference::getResolvedBy, adminId)
                .set(ReconciliationDifference::getResolveNote, request.resolveNote())
                .set(ReconciliationDifference::getResolvedAt, java.time.LocalDateTime.now()));
        if (updated == 0) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "RECON_DIFF_ALREADY_RESOLVED", "差异已被处置，请刷新");
        }
        return ApiResponse.ok(null);
    }
}
