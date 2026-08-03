package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.service.PostCommentService;
import com.cloudmart.community.vo.CommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comments")
@Tag(name = "评论互动", description = "评论点赞等互动接口")
@RequiredArgsConstructor
public class CommentController {

    private final PostCommentService postCommentService;

    @GetMapping("/mine")
    @Operation(summary = "我的评论", description = "获取当前用户发表的评论列表")
    public ApiResponse<List<CommentVO>> getMyComments(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<CommentVO> result = postCommentService.getMyComments(userId, page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @PostMapping("/{commentId}/like")
    @Operation(summary = "点赞评论", description = "用户点赞评论")
    public ApiResponse<Void> likeComment(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "评论ID", required = true) @PathVariable Long commentId) {
        postCommentService.likeComment(userId, commentId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{commentId}/like")
    @Operation(summary = "取消点赞评论", description = "用户取消评论点赞")
    public ApiResponse<Void> unlikeComment(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "评论ID", required = true) @PathVariable Long commentId) {
        postCommentService.unlikeComment(userId, commentId);
        return ApiResponse.ok(null);
    }
}
