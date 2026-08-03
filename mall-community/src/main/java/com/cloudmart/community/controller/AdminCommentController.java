package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.community.service.PostCommentService;
import com.cloudmart.community.vo.PostCommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/comments")
@Tag(name = "评论管理(后台)", description = "管理后台评论管理接口")
@RequiredArgsConstructor
public class AdminCommentController {

    private final PostCommentService postCommentService;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "评论列表", description = "管理后台分页查询评论列表，支持关键词和状态筛选")
    public ApiResponse<List<PostCommentVO>> adminListComments(
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "评论状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<PostCommentVO> result = postCommentService.adminListComments(keyword, status, page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新评论状态", description = "管理后台更新评论状态（隐藏/显示等）")
    public ApiResponse<Void> adminUpdateCommentStatus(
            @Parameter(description = "评论ID", required = true) @PathVariable("id") Long commentId,
            @Parameter(description = "状态信息") @RequestBody Map<String, Integer> body) {
        postCommentService.adminUpdateCommentStatus(commentId, body.get("status"));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除评论", description = "管理后台删除评论")
    public ApiResponse<Void> adminDeleteComment(
            @Parameter(description = "评论ID", required = true) @PathVariable("id") Long commentId) {
        postCommentService.adminDeleteComment(commentId);
        return ApiResponse.ok(null);
    }
}
