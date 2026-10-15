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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @PostMapping("/revisions/{revisionId}/decision")
    @Operation(summary = "审核决策",
            description = "携带 revisionId+submitSeq+expectedVersion 防旧批次覆盖（A07）；"
                    + "通过要求作者仍有发布资格（A33）；通过同事务切换 published 指针并写 Outbox 事件")
    public ApiResponse<SoftwareReviewDecisionVO> decision(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @org.springframework.web.bind.annotation.PathVariable("revisionId") Long revisionId,
            @RequestBody SoftwareReviewDecisionRequest request) {
        return ApiResponse.ok(softwarePublishService.reviewDecision(
                adminUserId, request.getIdempotencyKey(), request.getSoftwareId(), revisionId,
                request.getSubmitSeq(), request.getExpectedVersion(),
                Boolean.TRUE.equals(request.getApprove()),
                request.getReason(), request.getRequestId()));
    }
}
