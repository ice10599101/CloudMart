package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.dto.CreateReportRequest;
import com.cloudmart.community.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@Tag(name = "举报管理", description = "内容举报接口")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "提交举报", description = "用户举报帖子、评论或其他内容")
    public ApiResponse<Void> createReport(
            @Parameter(description = "举报人ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long reporterId,
            @Parameter(description = "创建举报请求") @Valid @RequestBody CreateReportRequest request) {
        reportService.createReport(reporterId, request);
        return ApiResponse.ok(null);
    }
}
