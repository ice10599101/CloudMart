package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.annotation.Idempotent;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.CreateWishCommentRequest;
import com.cloudmart.wish.service.WishCommentService;
import com.cloudmart.wish.vo.WishCommentCreateVO;
import com.cloudmart.wish.vo.WishCommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * 心愿评论 Controller（文档 2.2 节，Sprint 1.2）。
 *
 * <p>架构说明：文档原计划 Feign 调用 mall-community 评论 API，但社区评论强校验
 * posts 存在性无法服务 wish 资源，故本模块自建 wish_comment（决策见 V2 迁移脚本注释）。</p>
 *
 * <p>错误码：400 WISH_VALIDATION_ERROR（内容为空/超长/路径穿越/父评论无效）/
 * 404 WISH_NOT_FOUND / 403 WISH_FORBIDDEN（删除他人评论）。</p>
 */
@RestController
@RequestMapping("/wishes/{wishId}/comments")
@Tag(name = "心愿评论", description = "评论发表、列表（分页+回复）、删除")
@RequiredArgsConstructor
public class WishCommentController {

    private final WishCommentService wishCommentService;

    @PostMapping
    @Operation(summary = "发表评论", description = "content ≤500 字符，XSS 转义后存储；"
            + "敏感词命中先发后审（标记不阻断）；parentId 回复目标评论，二级回复自动扁平化")
    @SentinelResource("WISH_COMMENT_CREATE")
    @Idempotent(prefix = "wish-comment", ttl = 10)
    public ApiResponse<WishCommentCreateVO> createComment(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable Long wishId,
            @Parameter(description = "评论请求") @Valid @RequestBody CreateWishCommentRequest request) {
        WishCommentCreateVO vo = wishCommentService.createComment(userId, wishId, request);
        return ApiResponse.ok(vo);
    }

    @GetMapping
    @Operation(summary = "评论列表", description = "cursor 分页，时间倒序；仅展示 VISIBLE 评论，"
            + "管理后台下架后四端立即不展示")
    @SentinelResource("WISH_COMMENT_LIST")
    public ApiResponse<List<WishCommentVO>> listComments(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long viewerId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable Long wishId,
            @Parameter(description = "分页游标") @RequestParam(required = false) String cursor,
            @Parameter(description = "每页数量（1-50，默认 20）") @RequestParam(required = false) Integer pageSize) {
        WishCommentService.CommentPage page =
                wishCommentService.listComments(wishId, viewerId, cursor, pageSize);
        int safeSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        return ApiResponse.okWithCursor(page.records(), safeSize, page.nextCursor(), page.hasMore());
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "删除自己的评论", description = "软删，保留审计轨迹；仅作者本人可删除")
    @SentinelResource("WISH_COMMENT_DELETE")
    public ApiResponse<Void> deleteComment(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable Long wishId,
            @Parameter(description = "评论 ID", required = true) @PathVariable Long commentId) {
        wishCommentService.deleteComment(userId, wishId, commentId);
        return ApiResponse.ok(null);
    }
}
