package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.community.entity.SensitiveWord;
import com.cloudmart.community.service.ContentReviewService;
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
@RequestMapping("/admin/review")
@Tag(name = "内容审核(后台)", description = "管理后台内容审核与敏感词管理接口")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ContentReviewService contentReviewService;
    private final PostService postService;

    @GetMapping("/pending/posts")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "待审核帖子", description = "获取待审核帖子列表")
    public ApiResponse<List<PostVO>> listPendingPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<PostVO> result = postService.listPendingReviewPosts(page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @PutMapping("/posts/{id}/approve")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "审核通过帖子", description = "将帖子审核状态设为通过")
    public ApiResponse<Void> approvePost(
            @Parameter(description = "帖子ID") @PathVariable("id") Long postId) {
        postService.approvePost(postId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/posts/{id}/reject")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "审核拒绝帖子", description = "将帖子审核状态设为拒绝，并填写原因")
    public ApiResponse<Void> rejectPost(
            @Parameter(description = "帖子ID") @PathVariable("id") Long postId,
            @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "内容违规");
        postService.rejectPost(postId, reason);
        return ApiResponse.ok(null);
    }

    @GetMapping("/sensitive-words")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "敏感词列表", description = "分页查询敏感词库")
    public ApiResponse<List<SensitiveWord>> listSensitiveWords(
            @Parameter(description = "分类") @RequestParam(required = false) String category,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        List<SensitiveWord> words = contentReviewService.listSensitiveWords(category, page, size);
        return ApiResponse.ok(words);
    }

    @PostMapping("/sensitive-words")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "添加敏感词", description = "向敏感词库添加新词")
    public ApiResponse<SensitiveWord> addSensitiveWord(@RequestBody Map<String, Object> body) {
        String word = (String) body.get("word");
        String category = (String) body.getOrDefault("category", "GENERAL");
        int level = ((Number) body.getOrDefault("level", 1)).intValue();
        SensitiveWord sw = contentReviewService.addSensitiveWord(word, category, level);
        return ApiResponse.ok(sw);
    }

    @PutMapping("/sensitive-words/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新敏感词", description = "更新敏感词库中指定词的信息")
    public ApiResponse<SensitiveWord> updateSensitiveWord(
            @Parameter(description = "敏感词ID") @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body) {
        String word = (String) body.get("word");
        String category = (String) body.get("category");
        Integer level = body.get("level") != null ? ((Number) body.get("level")).intValue() : null;
        SensitiveWord sw = contentReviewService.updateSensitiveWord(id, word, category, level);
        return ApiResponse.ok(sw);
    }

    @DeleteMapping("/sensitive-words/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除敏感词", description = "从敏感词库删除指定词")
    public ApiResponse<Void> removeSensitiveWord(
            @Parameter(description = "敏感词ID") @PathVariable("id") Long id) {
        contentReviewService.removeSensitiveWord(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/sensitive-words/refresh")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "刷新敏感词缓存", description = "手动刷新敏感词内存缓存")
    public ApiResponse<Void> refreshCache() {
        contentReviewService.refreshCache();
        return ApiResponse.ok(null);
    }
}
