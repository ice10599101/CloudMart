package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.SoftwareCommentCreateRequest;
import com.cloudmart.software.dto.software.SoftwareReportCreateRequest;
import com.cloudmart.software.service.SoftwareCommentService;
import com.cloudmart.software.service.SoftwareFavoriteService;
import com.cloudmart.software.service.SoftwareReportService;
import com.cloudmart.software.vo.software.SoftwareCommentVO;
import com.cloudmart.software.vo.software.SoftwareReportCreatedVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/**
 * 评论与收藏端点（方案 7.3/4.4/4.5）：登录必达；宿主不可见时全部同步不可访问。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "小答软件-评论与收藏", description = "评论/回复/点赞/收藏（幂等关系）")
public class SoftwareInteractionController {

    private final SoftwareCommentService commentService;
    private final SoftwareFavoriteService favoriteService;
    private final SoftwareReportService reportService;

    @PostMapping("/{id}/comments")
    @Operation(summary = "发表评论",
            description = "机审绑定结果；VISIBLE 直发计数+通知，转人工 PENDING 仅本人可见；幂等")
    public ApiResponse<SoftwareCommentVO> createComment(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SoftwareCommentCreateRequest request) {
        return ApiResponse.ok(commentService.create(userId, id, idempotencyKey,
                request.getContent(), request.getParentId(), request.getReplyToCommentId()));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "根评论分页",
            description = "最新优先；双向拉黑过滤；每楼预览 3 条回复+replyHasMore；游客无待审")
    public ApiResponse<Page<SoftwareCommentVO>> comments(
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long viewerId,
            @PathVariable("id") Long id,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(commentService.pageRoots(id, viewerId, page, size));
    }

    @GetMapping("/{id}/comments/{rootId}/replies")
    @Operation(summary = "楼中楼分页", description = "时间升序；继承宿主可见性与隐私过滤")
    public ApiResponse<Page<SoftwareCommentVO>> replies(
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long viewerId,
            @PathVariable("id") Long id,
            @PathVariable("rootId") Long rootId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.ok(commentService.pageReplies(id, rootId, viewerId, page, size));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @Operation(summary = "删除本人评论", description = "软删保留占位；可见评论删除计数-1")
    public ApiResponse<Void> deleteComment(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @PathVariable("commentId") Long commentId) {
        commentService.delete(userId, id, commentId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/comments/{commentId}/like")
    @Operation(summary = "点赞", description = "幂等 PUT；仅可见评论；唯一键防重")
    public ApiResponse<Void> like(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @PathVariable("commentId") Long commentId) {
        commentService.like(userId, id, commentId, true);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/comments/{commentId}/like")
    @Operation(summary = "取消点赞", description = "幂等 DELETE；计数不为负")
    public ApiResponse<Void> unlike(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @PathVariable("commentId") Long commentId) {
        commentService.like(userId, id, commentId, false);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/favorite")
    @Operation(summary = "收藏", description = "幂等 PUT；公开可见前提；唯一键防重")
    public ApiResponse<Void> favorite(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id) {
        favoriteService.favorite(userId, id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/favorite")
    @Operation(summary = "取消收藏", description = "幂等 DELETE；软件下架也成功且不泄露信息")
    public ApiResponse<Void> unfavorite(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id) {
        favoriteService.unfavorite(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/reports")
    @Operation(summary = "举报软件或评论",
            description = "targetType=SOFTWARE/SOFTWARE_COMMENT；仅可举报可见目标；"
                    + "Idempotency-Key 幂等（software_operation）；处置由管理员显式选择")
    public ApiResponse<SoftwareReportCreatedVO> report(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody SoftwareReportCreateRequest request) {
        return ApiResponse.ok(reportService.create(userId, id, idempotencyKey, request));
    }
}
