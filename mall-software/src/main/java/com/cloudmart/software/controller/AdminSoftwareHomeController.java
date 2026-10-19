package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.AdminHomeSectionReorderRequest;
import com.cloudmart.software.dto.software.AdminHomeSectionSaveRequest;
import com.cloudmart.software.service.SoftwareHomeSectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 首页内容管理（UX01）：区块 CRUD/启停/重排/预览。
 * 类级 INTERNAL 守卫由 SecurityConfig 与 T02 口径保证（mall-admin 代理 + 显式权限）；
 * 保存/启停/删除用幂等键，不引入配置 expectedVersion。
 */
@RestController
@RequestMapping("/admin/software/home-sections")
@RequiredArgsConstructor
@Tag(name = "小答软件-首页内容管理", description = "UX01 首页固定区块管理（admin）")
public class AdminSoftwareHomeController {

    private final SoftwareHomeSectionService homeSectionService;

    @GetMapping
    @Operation(summary = "区块管理列表", description = "含未启用；编辑视图（含 MANUAL 条目）")
    public ApiResponse<List<Map<String, Object>>> sections() {
        return ApiResponse.ok(homeSectionService.adminSections());
    }

    @PostMapping
    @Operation(summary = "新增区块", description = "kind/data_source 白名单；MANUAL 条目同事务替换；幂等键")
    public ApiResponse<Long> create(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminHomeSectionSaveRequest request) {
        return ApiResponse.ok(homeSectionService.saveSection(null, request, idempotencyKey));
    }

    @PatchMapping("/{sectionId}")
    @Operation(summary = "更新区块", description = "部分字段更新语义同全量（本接口整体保存）；幂等键")
    public ApiResponse<Long> update(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("sectionId") Long sectionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminHomeSectionSaveRequest request) {
        return ApiResponse.ok(homeSectionService.saveSection(sectionId, request, idempotencyKey));
    }

    @PostMapping("/{sectionId}/toggle")
    @Operation(summary = "启用/停用", description = "停用后公共聚合立即不返回；幂等键")
    public ApiResponse<Boolean> toggle(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("sectionId") Long sectionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        Object value = body == null ? null : body.get("enabled");
        if (!(value instanceof Boolean enabled)) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_HOME_INVALID", "缺少 enabled 布尔值");
        }
        return ApiResponse.ok(homeSectionService.toggle(sectionId, enabled, idempotencyKey));
    }

    @DeleteMapping("/{sectionId}")
    @Operation(summary = "删除区块", description = "软删除+条目清理；重复删除幂等")
    public ApiResponse<Boolean> delete(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("sectionId") Long sectionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(homeSectionService.delete(sectionId, idempotencyKey));
    }

    @PostMapping("/reorder")
    @Operation(summary = "原子重排", description = "完整集合校验（缺失/重复拒绝）后按提交顺序重排")
    public ApiResponse<Boolean> reorder(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @RequestBody AdminHomeSectionReorderRequest request) {
        return ApiResponse.ok(homeSectionService.reorder(request.getSectionIds()));
    }

    @GetMapping("/preview")
    @Operation(summary = "首页预览", description = "含未启用/不在投放期内容（LIVE/OFFLINE 标记）；只对管理员可读")
    public ApiResponse<List<Map<String, Object>>> preview(
            @RequestParam(value = "platform", required = false) String platform) {
        return ApiResponse.ok(homeSectionService.preview(platform));
    }
}
