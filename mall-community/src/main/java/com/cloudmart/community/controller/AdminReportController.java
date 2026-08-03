package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.dto.HandleReportRequest;
import com.cloudmart.community.service.ReportService;
import com.cloudmart.community.vo.ReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/reports")
@Tag(name = "举报管理(后台)", description = "管理后台举报处理接口")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "举报列表", description = "管理后台分页查询举报列表，支持按状态和目标类型筛选")
    public ApiResponse<List<ReportVO>> adminListReports(
            @Parameter(description = "举报状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "目标类型") @RequestParam(required = false) String targetType,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<ReportVO> result = reportService.adminListReports(status, targetType, page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @PutMapping("/{id}/handle")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "处理举报", description = "管理后台处理举报，更新状态并填写处理备注")
    public ApiResponse<Void> handleReport(
            @Parameter(description = "处理人ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long handlerId,
            @Parameter(description = "举报ID", required = true) @PathVariable("id") Long reportId,
            @Parameter(description = "处理举报请求") @Valid @RequestBody HandleReportRequest request) {
        reportService.handleReport(handlerId, reportId, request);
        return ApiResponse.ok(null);
    }
}
