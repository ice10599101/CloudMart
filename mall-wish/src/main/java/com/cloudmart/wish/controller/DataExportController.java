package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.DataExport;
import com.cloudmart.wish.service.DataExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据导出 Controller（Sprint 3.6 补齐，合规 34.2）。
 *
 * <p>全部委托 {@link DataExportService}：创建任务并异步聚合生成 JSON，查询/下载
 * 均做归属校验及过期校验（7 天有效期）。下载端点以附件方式直接返回导出内容。</p>
 */
@RestController
@RequestMapping("/my")
@Tag(name = "数据导出", description = "用户数据导出（合规 34.2）")
@RequiredArgsConstructor
public class DataExportController {

    private final DataExportService dataExportService;

    @PostMapping("/export")
    @Operation(summary = "触发数据导出", description = "创建异步导出任务并触发后台生成")
    public ApiResponse<DataExport> createExport(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(dataExportService.createExport(userId));
    }

    @GetMapping("/export/{taskId}")
    @Operation(summary = "查询导出任务状态")
    public ApiResponse<DataExport> getExport(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "任务 ID", required = true) @PathVariable Long taskId) {
        return ApiResponse.ok(dataExportService.getTask(userId, taskId));
    }

    @GetMapping("/export/{taskId}/download")
    @Operation(summary = "下载导出内容", description = "以附件方式返回导出 JSON（归属校验 + 7 天过期校验）")
    public ResponseEntity<byte[]> downloadExport(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "任务 ID", required = true) @PathVariable Long taskId) {
        String content = dataExportService.loadContent(userId, taskId);
        if (content == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "导出内容不存在或已过期，请重新发起导出");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=wish-data-export-" + taskId + ".json")
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/exports")
    @Operation(summary = "导出任务列表")
    public ApiResponse<List<DataExport>> listExports(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(dataExportService.listTasks(userId));
    }
}