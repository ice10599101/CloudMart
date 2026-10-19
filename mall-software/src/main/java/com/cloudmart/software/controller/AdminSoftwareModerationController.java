package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.AdminFeedbackMarkRequest;
import com.cloudmart.software.dto.software.AdminReportHandleRequest;
import com.cloudmart.software.service.AdminSoftwareModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端举报与链接反馈（方案 5.3/6.12）：仅 mall-admin 代理可达；
 * 处置动作显式选择，状态/审计/Outbox 同软件库事务。
 */
@RestController
@RequestMapping("/admin/software")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理端-举报与反馈", description = "软件举报处置/失效反馈闭环")
public class AdminSoftwareModerationController {

    private final AdminSoftwareModerationService adminSoftwareModerationService;

    @GetMapping("/reports")
    @Operation(summary = "举报列表", description = "status 过滤；按创建时间升序")
    public ApiResponse<Page<Map<String, Object>>> reports(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(adminSoftwareModerationService.reportPage(status, page, size));
    }

    @PostMapping("/reports/{reportId}/handle")
    @Operation(summary = "举报处置",
            description = "RESOLVED+显式 action（NONE/BAN_SOFTWARE/HIDE_COMMENT）或 REJECTED；"
                    + "同软件库事务；重复处置幂等")
    public ApiResponse<Boolean> handleReport(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("reportId") Long reportId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminReportHandleRequest request) {
        return ApiResponse.ok(adminSoftwareModerationService.handleReport(
                adminUserId, reportId, idempotencyKey, request));
    }

    @GetMapping("/link-feedbacks")
    @Operation(summary = "失效反馈列表", description = "status 过滤")
    public ApiResponse<Page<Map<String, Object>>> feedbacks(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "reasonCode", required = false) String reasonCode,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(adminSoftwareModerationService.feedbackPage(status, reasonCode, page, size));
    }

    @PostMapping("/link-feedbacks/{feedbackId}/mark")
    @Operation(summary = "反馈标记",
            description = "RESOLVED 必须携带修复 revisionId（保留 original 指纹与修复 revisionId）")
    public ApiResponse<Boolean> markFeedback(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("feedbackId") Long feedbackId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminFeedbackMarkRequest request) {
        return ApiResponse.ok(adminSoftwareModerationService.markFeedback(
                adminUserId, feedbackId, idempotencyKey, request));
    }
}
