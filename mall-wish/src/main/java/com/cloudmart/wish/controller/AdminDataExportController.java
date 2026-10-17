package com.cloudmart.wish.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.entity.DataExport;
import com.cloudmart.wish.repository.DataExportMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OPS-03：心愿数据导出运维处置（异常处理中心扩展域）。
 * mall-admin 经服务令牌代理：失败任务查询 + 受控重试（FAILED→PENDING 重排，
 * 不跳过任何业务校验，重试由持续 worker 认领执行）；脱敏视图不含 content_enc。
 */
@RestController
@RequestMapping("/admin/exports")
@Tag(name = "内部-心愿数据导出运维", description = "OPS-03：导出任务失败查询与受控重试")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
public class AdminDataExportController {

    private final DataExportMapper exportMapper;

    /** 运维分页视图（脱敏：不含 content_enc/content 字段） */
    private Map<String, Object> toView(DataExport t) {
        return Map.of(
                "id", t.getId(),
                "userId", t.getUserId(),
                "status", t.getStatus(),
                "failReason", t.getFailReason() == null ? "" : t.getFailReason(),
                "createdAt", t.getCreatedAt() == null ? "" : t.getCreatedAt().toString(),
                "updatedAt", t.getUpdatedAt() == null ? "" : t.getUpdatedAt().toString(),
                "expiresAt", t.getExpiresAt() == null ? "" : t.getExpiresAt().toString());
    }

    @GetMapping
    @Operation(summary = "导出任务分页", description = "OPS-03：按状态过滤（含 FAILED 原因），脱敏视图不含密文")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DataExport> result = exportMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(size, 100)),
                new LambdaQueryWrapper<DataExport>()
                        .eq(status != null && !status.isBlank(), DataExport::getStatus, status)
                        .orderByDesc(DataExport::getId));
        return ApiResponse.ok(Map.of(
                "records", result.getRecords().stream().map(this::toView).toList(),
                "total", result.getTotal()));
    }

    public record RetryExportRequest(String reason) {
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "受控重试失败任务", description = "OPS-03：仅 FAILED 可重置为 PENDING（清失败原因重新排队，"
            + "由持续 worker 认领执行）；受理≠成功；不提供跳过校验的强制成功")
    public ApiResponse<Map<String, Object>> retry(
            @PathVariable long id,
            @RequestBody RetryExportRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "重试必须填写运营原因（审计）");
        }
        DataExport task = exportMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("EXPORT_NOT_FOUND", "导出任务不存在");
        }
        if (!"FAILED".equals(task.getStatus())) {
            throw new BusinessException("EXPORT_STATUS_CONFLICT", "仅 FAILED 任务可重试（当前: " + task.getStatus() + "）");
        }
        int updated = exportMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DataExport>()
                        .eq(DataExport::getId, id)
                        .eq(DataExport::getStatus, "FAILED")
                        .set(DataExport::getStatus, "PENDING")
                        .set(DataExport::getFailReason, null));
        return ApiResponse.ok(Map.of(
                "id", id,
                "accepted", updated > 0,
                "status", updated > 0 ? "PENDING" : task.getStatus(),
                "reason", request.reason()));
    }
}
