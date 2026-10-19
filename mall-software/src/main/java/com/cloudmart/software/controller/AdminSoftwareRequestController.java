package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.service.SoftwareRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
 * UX06：软件需求管理（admin；类级 INTERNAL 守卫）。审核 + 标记"已找到"。
 * 已找到目标必须为当前可见软件；不能让投稿者自己刷"解决数"。
 */
@RestController
@RequestMapping("/admin/software/requests")
@RequiredArgsConstructor
@Tag(name = "小答软件-需求管理", description = "UX06 需求审核与已找到标记（admin 代理）")
public class AdminSoftwareRequestController {

    private final SoftwareRequestService requestService;

    @GetMapping
    @Operation(summary = "需求管理分页", description = "auditStatus/status 筛选；含来源关键词与匹配可见性")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(value = "auditStatus", required = false) String auditStatus,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return ApiResponse.ok(requestService.adminPage(auditStatus, status, page, size));
    }

    @PostMapping("/{requestId}/audit")
    @Operation(summary = "审核", description = "PENDING→APPROVED/REJECTED；拒绝必须原因")
    public ApiResponse<Boolean> audit(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("requestId") Long requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        Object approvedObj = body == null ? null : body.get("approved");
        if (!(approvedObj instanceof Boolean approved)) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_REQUEST_INVALID", "缺少 approved 布尔值");
        }
        Object reasonObj = body == null ? null : body.get("reason");
        return ApiResponse.ok(requestService.audit(adminUserId, requestId, approved,
                reasonObj == null ? null : String.valueOf(reasonObj), idempotencyKey));
    }

    @PostMapping("/{requestId}/matched")
    @Operation(summary = "标记已找到", description = "目标必须当前可见；理由写入 matchedReason")
    public ApiResponse<Boolean> markMatched(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("requestId") Long requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        Object softwareObj = body == null ? null : body.get("softwareId");
        Long softwareId;
        try {
            softwareId = softwareObj == null ? null : Long.parseLong(String.valueOf(softwareObj));
        } catch (NumberFormatException e) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_REQUEST_INVALID", "softwareId 非法");
        }
        Object reasonObj = body == null ? null : body.get("reason");
        return ApiResponse.ok(requestService.markMatched(adminUserId, requestId, softwareId,
                reasonObj == null ? null : String.valueOf(reasonObj), idempotencyKey));
    }
}
