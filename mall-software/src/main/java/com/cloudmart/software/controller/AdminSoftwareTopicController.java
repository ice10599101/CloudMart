package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.AdminTopicSaveRequest;
import com.cloudmart.software.service.AdminSoftwareTopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端专题维护（方案 7.5/10.6，P1）：仅 mall-admin 代理可达；
 * 权限裁决（software:topic）在代理层 @RequiresPermission，本层以 INTERNAL 为边界。
 */
@RestController
@RequestMapping("/admin/software")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理端-专题", description = "专题与软件排序项整体维护（P1）")
public class AdminSoftwareTopicController {

    private final AdminSoftwareTopicService adminSoftwareTopicService;

    @GetMapping("/topics")
    @Operation(summary = "专题分页（含草稿/禁用）")
    public ApiResponse<Page<Map<String, Object>>> page(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(adminSoftwareTopicService.page(page, size));
    }

    @GetMapping("/topics/{topicId}")
    @Operation(summary = "专题编辑读取", description = "专题 + 原始清单（不过滤可见性）")
    public ApiResponse<Map<String, Object>> detail(@PathVariable("topicId") Long topicId) {
        return ApiResponse.ok(adminSoftwareTopicService.detail(topicId));
    }

    @PostMapping("/topics")
    @Operation(summary = "新增专题", description = "清单全量替换；slug 唯一")
    public ApiResponse<Long> create(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminTopicSaveRequest request) {
        return ApiResponse.ok(adminSoftwareTopicService.save(
                adminUserId, null, idempotencyKey, request));
    }

    @PutMapping("/topics/{topicId}")
    @Operation(summary = "更新专题", description = "含上线/下线（status）与清单整体替换")
    public ApiResponse<Long> update(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("topicId") Long topicId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminTopicSaveRequest request) {
        return ApiResponse.ok(adminSoftwareTopicService.save(
                adminUserId, topicId, idempotencyKey, request));
    }

    @DeleteMapping("/topics/{topicId}")
    @Operation(summary = "禁用专题", description = "DELETE 语义为禁用，不做物理删除")
    public ApiResponse<Boolean> disable(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("topicId") Long topicId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(adminSoftwareTopicService.disable(
                adminUserId, topicId, idempotencyKey));
    }
}
