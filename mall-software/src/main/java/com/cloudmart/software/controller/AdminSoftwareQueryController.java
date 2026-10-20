package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.AdminCommentModerateRequest;
import com.cloudmart.software.service.AdminSoftwareQueryService;
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
 * 管理端查询与评论处置（方案 7.5/9.6）：仅 mall-admin 代理可达。
 * 权限裁决（software:review 等）在代理层 @RequiresPermission，本层以 INTERNAL 为边界。
 */
@RestController
@RequestMapping("/admin/software")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理端-软件查询与评论", description = "待审列表/后台详情/评论管理")
public class AdminSoftwareQueryController {

    private final AdminSoftwareQueryService adminSoftwareQueryService;

    @GetMapping("/revisions/pending")
    @Operation(summary = "待审/检查列表",
            description = "status=PENDING（默认）或 CHECKING；按提交时间升序，含等待时长与检查任务状态")
    public ApiResponse<Page<Map<String, Object>>> pending(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(adminSoftwareQueryService.pendingPage(status, page, size));
    }

    @GetMapping("/revisions/{revisionId}")
    @Operation(summary = "审核快照对比",
            description = "目标快照完整内容（含链接/提取码，仅审核域）+ 当前线上快照（可空=首次发布）；"
                    + "前端标出链接域名、提取码、来源声明、正文与截图变化")
    public ApiResponse<Map<String, Object>> reviewDetail(
            @PathVariable("revisionId") Long revisionId) {
        return ApiResponse.ok(adminSoftwareQueryService.reviewDetail(revisionId));
    }

    @GetMapping("/{softwareId}/admin-detail")
    @Operation(summary = "后台完整详情",
            description = "全快照+状态+最近审核历史（独立鉴权可读秘密字段）")
    public ApiResponse<Map<String, Object>> adminDetail(
            @PathVariable("softwareId") Long softwareId) {
        return ApiResponse.ok(adminSoftwareQueryService.adminDetail(softwareId));
    }

    @GetMapping("/comments")
    @Operation(summary = "评论管理全局列表",
            description = "不指定 softwareId 时跨软件；含全部状态；HIDDEN/DELETED 不回传原文")
    public ApiResponse<Page<Map<String, Object>>> commentsGlobal(
            @RequestParam(value = "softwareId", required = false) Long softwareId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(
                adminSoftwareQueryService.commentPage(softwareId, status, page, size));
    }

    @GetMapping("/{softwareId}/comments")
    @Operation(summary = "评论管理列表",
            description = "含 HIDDEN/PENDING/DELETED；HIDDEN/DELETED 不回传原文（与前台一致）")
    public ApiResponse<Page<Map<String, Object>>> comments(
            @PathVariable("softwareId") Long softwareId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(adminSoftwareQueryService.commentPage(softwareId, status, page, size));
    }

    @PostMapping("/{softwareId}/comments/{commentId}/moderate")
    @Operation(summary = "评论人工处置",
            description = "APPROVE（PENDING→VISIBLE 计数+1+事件）/HIDE/RESTORE/REJECT；幂等")
    public ApiResponse<Boolean> moderateComment(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("softwareId") Long softwareId,
            @PathVariable("commentId") Long commentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminCommentModerateRequest request) {
        return ApiResponse.ok(adminSoftwareQueryService.moderateComment(
                adminUserId, softwareId, commentId, idempotencyKey,
                request.getAction(), request.getReason()));
    }
}
