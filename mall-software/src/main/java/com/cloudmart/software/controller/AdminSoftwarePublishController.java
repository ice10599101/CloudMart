package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.SoftwareReviewDecisionRequest;
import com.cloudmart.software.service.SoftwarePublishService;
import com.cloudmart.software.vo.software.SoftwareReviewDecisionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小答软件管理端原生端点（方案 7.5/9.6）：仅 mall-admin 管理代理可达
 * （iss=mall-admin、scope=software:admin，网关同步拦截外部直达）。
 * 操作管理员身份来自 mall-admin 注入的 X-User-Id（服务令牌路径强校验保证可信）。
 * 权限注解（software:review 等）由 mall-admin 代理层 @RequiresPermission 裁决，
 * 本层以 INTERNAL 服务身份为边界（纵深防御双层）。
 */
@RestController
@RequestMapping("/admin/software")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理端-小答软件", description = "mall-admin 代理专用：审核决策等")
public class AdminSoftwarePublishController {

    private final SoftwarePublishService softwarePublishService;
    private final com.cloudmart.software.service.AdminSoftwareSourceService adminSoftwareSourceService;

    @org.springframework.web.bind.annotation.DeleteMapping("/items/{softwareId}")
    @Operation(summary = "管理员删除软件", description = "删除后前台立即不可见且无法恢复（数据留有审计记录），幂等")
    public ApiResponse<Boolean> delete(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @org.springframework.web.bind.annotation.PathVariable("softwareId") Long softwareId) {
        softwarePublishService.adminDelete(adminUserId, softwareId);
        return ApiResponse.ok(true);
    }

    @PostMapping("/revisions/{revisionId}/decision")
    @Operation(summary = "审核决策",
            description = "定位不可变提交记录 revisionId；通过要求作者仍有发布资格（A33）；"
                    + "通过同事务切换 published 指针并写 Outbox 事件")
    public ApiResponse<SoftwareReviewDecisionVO> decision(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @org.springframework.web.bind.annotation.PathVariable("revisionId") Long revisionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody SoftwareReviewDecisionRequest request) {
        return ApiResponse.ok(softwarePublishService.reviewDecision(
                adminUserId, idempotencyKey, request.getSoftwareId(), revisionId,
                Boolean.TRUE.equals(request.getApprove()),
                request.getReason(), request.getRequestId()));
    }

    @GetMapping("/{softwareId}/sources")
    @Operation(summary = "来源清单（SW06）",
            description = "当前线上快照按版本/平台/架构列出；链接默认脱敏，不返回提取码/解压密码")
    public ApiResponse<java.util.List<com.cloudmart.software.vo.software.AdminSoftwareSourceVO>> sources(
            @org.springframework.web.bind.annotation.PathVariable("softwareId") Long softwareId) {
        return ApiResponse.ok(adminSoftwareSourceService.listSources(softwareId));
    }

    @PostMapping("/{softwareId}/sources/state")
    @Operation(summary = "来源状态处置",
            description = "核实（AVAILABLE/INVALID/SUSPECTED 按当前指纹应用）/禁用 DISABLED（无条件）/解除；"
                    + "平台禁用优先于反馈，作者改链不能自行恢复（4.3）")
    public ApiResponse<Boolean> sourceState(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @org.springframework.web.bind.annotation.PathVariable("softwareId") Long softwareId,
            @RequestBody com.cloudmart.software.dto.software.SoftwareSourceStateRequest request) {
        return ApiResponse.ok(adminSoftwareSourceService.applyState(
                adminUserId, softwareId, request.getSourceKey(),
                request.getAvailability(), request.getReason()));
    }
}
