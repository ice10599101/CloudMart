package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.SourceAppealHandleRequest;
import com.cloudmart.software.service.SoftwareSourceAppealService;
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
 * 管理端-作者来源申诉（SF08 补批）：仅 mall-admin 代理可达。
 * 与来源处置并列查看；ACCEPTED 仅恢复为 UNKNOWN 待重检，不直接判可用。
 */
@RestController
@RequestMapping("/admin/software/source-appeals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理端-来源申诉", description = "作者来源申诉列表与处理（software:link）")
public class AdminSoftwareSourceAppealController {

    private final SoftwareSourceAppealService appealService;

    @GetMapping
    @Operation(summary = "申诉列表", description = "status=PENDING/ACCEPTED/REJECTED；含处置快照")
    public ApiResponse<Page<Map<String, Object>>> page(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(appealService.page(status, page, size));
    }

    @PostMapping("/{appealId}/handle")
    @Operation(summary = "处理申诉",
            description = "ACCEPTED/REJECTED + 处理说明；只允许处理一次，重复同结论幂等")
    public ApiResponse<Boolean> handle(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("appealId") Long appealId,
            @RequestBody SourceAppealHandleRequest request) {
        return ApiResponse.ok(appealService.handle(adminUserId, appealId, request));
    }
}
