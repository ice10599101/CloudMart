package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.service.PostService;
import com.cloudmart.community.vo.PostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/posts")
@Tag(name = "帖子管理(后台)", description = "管理后台帖子管理接口")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "帖子列表", description = "管理后台分页查询帖子列表，支持关键词、状态、用户ID筛选")
    public ApiResponse<List<PostVO>> adminListPosts(
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "帖子状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<PostVO> result = postService.adminListPosts(keyword, status, userId, page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新帖子状态", description = "管理后台更新帖子状态（隐藏/显示/删除等）")
    public ApiResponse<Void> adminUpdatePostStatus(
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId,
            @Parameter(description = "状态信息") @RequestBody Map<String, Integer> body) {
        postService.adminUpdatePostStatus(postId, body.get("status"));
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/top")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "切换置顶", description = "管理后台切换帖子置顶状态")
    public ApiResponse<Void> adminToggleTop(
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId,
            @Parameter(description = "置顶信息") @RequestBody Map<String, Boolean> body) {
        postService.adminToggleTop(postId, body.get("isTop"));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除帖子", description = "管理后台删除帖子")
    public ApiResponse<Void> adminDeletePost(
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId) {
        postService.adminDeletePost(postId);
        return ApiResponse.ok(null);
    }
}
