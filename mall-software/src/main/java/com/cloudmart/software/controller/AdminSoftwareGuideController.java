package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.AdminGuideSaveRequest;
import com.cloudmart.software.service.SoftwareGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * UX04：教程元数据管理（admin；/admin 守卫由 SecurityConfig INTERNAL 规则保证）。
 * 创建时预检社区文章公开状态；读取时实时校验并标注 articlePublic。
 */
@RestController
@RequestMapping("/admin/software/guides")
@RequiredArgsConstructor
@Tag(name = "小答软件-教程管理", description = "UX04 教程元数据管理（admin 代理）")
public class AdminSoftwareGuideController {

    private final SoftwareGuideService guideService;

    @GetMapping
    @Operation(summary = "教程管理分页",
            description = "含未启用/文章非公开/关联软件失效标注；maintenanceOnly=维护队列")
    public ApiResponse<Map<String, Object>> guides(
            @RequestParam(value = "guideType", required = false) String guideType,
            @RequestParam(value = "maintenanceOnly", defaultValue = "false") boolean maintenanceOnly,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return ApiResponse.ok(guideService.adminGuides(guideType, maintenanceOnly, page, size));
    }

    @PostMapping
    @Operation(summary = "新增教程", description = "articleId 预检公开状态；关联软件≤3；幂等键")
    public ApiResponse<Long> create(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminGuideSaveRequest request) {
        return ApiResponse.ok(guideService.saveGuide(null, request, idempotencyKey));
    }

    @PostMapping("/{guideId}")
    @Operation(summary = "更新教程", description = "整体保存；幂等键")
    public ApiResponse<Long> update(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("guideId") Long guideId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminGuideSaveRequest request) {
        return ApiResponse.ok(guideService.saveGuide(guideId, request, idempotencyKey));
    }

    @PostMapping("/{guideId}/toggle")
    @Operation(summary = "启用/停用", description = "停用后公开入口立即隐藏")
    public ApiResponse<Boolean> toggle(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("guideId") Long guideId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        Object value = body == null ? null : body.get("enabled");
        if (!(value instanceof Boolean enabled)) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_GUIDE_INVALID", "缺少 enabled 布尔值");
        }
        return ApiResponse.ok(guideService.toggle(guideId, enabled, idempotencyKey));
    }

    @DeleteMapping("/{guideId}")
    @Operation(summary = "删除教程", description = "软删除；重复删除幂等")
    public ApiResponse<Boolean> delete(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("guideId") Long guideId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(guideService.delete(guideId, idempotencyKey));
    }
}
