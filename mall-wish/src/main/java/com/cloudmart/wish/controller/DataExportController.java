package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.DataExport;
import com.cloudmart.wish.repository.DataExportMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 数据导出 Controller（Sprint 3.6 补齐，合规 34.2）。
 */
@RestController
@RequestMapping("/my")
@Tag(name = "数据导出", description = "用户数据导出（合规 34.2）")
@RequiredArgsConstructor
public class DataExportController {

    private final com.cloudmart.wish.repository.DataExportMapper exportMapper;

    @PostMapping("/export")
    @Operation(summary = "触发数据导出", description = "创建异步导出任务")
    public ApiResponse<DataExport> createExport(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        DataExport export = new DataExport();
        export.setUserId(userId);
        export.setStatus("PENDING");
        exportMapper.insert(export);
        return ApiResponse.ok(export);
    }

    @GetMapping("/export/{taskId}")
    @Operation(summary = "查询导出任务状态")
    public ApiResponse<DataExport> getExport(
            @Parameter(description = "任务 ID", required = true) @PathVariable Long taskId) {
        return ApiResponse.ok(exportMapper.selectById(taskId));
    }

    @GetMapping("/exports")
    @Operation(summary = "导出任务列表")
    public ApiResponse<List<DataExport>> listExports(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(exportMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DataExport>()
                        .eq(DataExport::getUserId, userId)
                        .orderByDesc(DataExport::getId)));
    }
}
