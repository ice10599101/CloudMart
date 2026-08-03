package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.dto.CreateCommentRequest;
import com.cloudmart.community.service.PostCommentService;
import com.cloudmart.community.vo.PostCommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts/{postId}/comments")
@Tag(name = "评论管理", description = "帖子评论接口")
@RequiredArgsConstructor
public class PostCommentController {

    private final PostCommentService postCommentService;

    @PostMapping
    @Operation(summary = "发表评论", description = "用户对帖子发表评论，支持回复")
    public ApiResponse<PostCommentVO> createComment(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "帖子ID", required = true) @PathVariable Long postId,
            @Parameter(description = "创建评论请求") @Valid @RequestBody CreateCommentRequest request) {
        PostCommentVO vo = postCommentService.createComment(userId, postId, request);
        return ApiResponse.ok(vo);
    }

    @GetMapping
    @Operation(summary = "评论列表", description = "获取帖子下的评论列表，支持分页")
    public ApiResponse<List<PostCommentVO>> getComments(
            @Parameter(description = "帖子ID", required = true) @PathVariable Long postId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "当前用户ID") @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        Page<PostCommentVO> result = postCommentService.getComments(postId, page, size, userId);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "删除评论", description = "用户删除自己的评论")
    public ApiResponse<Void> deleteComment(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "评论ID", required = true) @PathVariable Long commentId) {
        postCommentService.deleteComment(userId, commentId);
        return ApiResponse.ok(null);
    }
}
