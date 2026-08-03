package com.cloudmart.community.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.dto.CreatePostRequest;
import com.cloudmart.community.dto.UpdatePostRequest;
import com.cloudmart.community.service.PostService;
import com.cloudmart.community.service.PostShareService;
import com.cloudmart.community.service.SearchService;
import com.cloudmart.community.vo.PostVO;
import com.cloudmart.community.vo.PostShareVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@Tag(name = "帖子管理", description = "社区帖子发布、查询、互动接口")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostShareService postShareService;
    private final SearchService searchService;

    @PostMapping
    @Operation(summary = "发布帖子", description = "用户发布新帖子")
    @SentinelResource(value = "POST_CREATE", blockHandler = "createPostBlockHandler")
    public ApiResponse<PostVO> createPost(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "创建帖子请求") @Valid @RequestBody CreatePostRequest request) {
        PostVO vo = postService.createPost(userId, request);
        return ApiResponse.ok(vo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新帖子", description = "用户更新自己的帖子")
    public ApiResponse<PostVO> updatePost(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId,
            @Parameter(description = "更新帖子请求") @Valid @RequestBody UpdatePostRequest request) {
        PostVO vo = postService.updatePost(userId, postId, request);
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除帖子", description = "用户删除自己的帖子")
    public ApiResponse<Void> deletePost(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId) {
        postService.deletePost(userId, postId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "帖子详情", description = "获取帖子详情，浏览数自增")
    public ApiResponse<PostVO> getPostDetail(
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId,
            @Parameter(description = "当前用户ID") @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        PostVO vo = postService.getPostDetail(postId, userId);
        return ApiResponse.ok(vo);
    }

    @GetMapping("/feed")
    @Operation(summary = "帖子信息流", description = "按标签页获取帖子列表（推荐/关注/最新）")
    @SentinelResource(value = "FEED_QUERY", blockHandler = "feedBlockHandler")
    public ApiResponse<List<PostVO>> getFeedPosts(
            @Parameter(description = "标签页: recommend/following/latest") @RequestParam(defaultValue = "recommend") String tab,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "当前用户ID") @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        Page<PostVO> result = postService.getFeedPosts(tab, page, size, userId);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @GetMapping("/feed/following")
    @Operation(summary = "关注动态", description = "获取当前用户关注的人的帖子列表")
    public ApiResponse<List<PostVO>> getFollowingFeed(
            @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (userId == null) {
            return ApiResponse.ok(List.of(), new ApiResponse.Meta(page, size, 0L));
        }
        Page<PostVO> result = postService.getFollowingFeed(userId, page, size);
        return ApiResponse.ok(result.getRecords(), new ApiResponse.Meta(page, size, result.getTotal()));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索帖子", description = "按关键词搜索帖子")
    @SentinelResource(value = "SEARCH_QUERY", blockHandler = "searchBlockHandler")
    public ApiResponse<List<PostVO>> searchPosts(
            @Parameter(description = "搜索关键词", required = true) @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "当前用户ID") @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        searchService.recordSearch(userId, keyword);
        Page<PostVO> result = postService.searchPosts(keyword, page, size, userId);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "用户帖子列表", description = "获取指定用户的帖子列表")
    public ApiResponse<List<PostVO>> getUserPosts(
            @Parameter(description = "目标用户ID", required = true) @PathVariable Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "当前用户ID") @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long currentUserId) {
        Page<PostVO> result = postService.getUserPosts(userId, page, size, currentUserId);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @GetMapping("/drafts")
    @Operation(summary = "我的草稿", description = "获取当前用户的草稿列表")
    public ApiResponse<List<PostVO>> getUserDrafts(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<PostVO> result = postService.getUserDrafts(userId, page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @GetMapping("/liked")
    @Operation(summary = "我点赞的帖子", description = "获取当前用户点赞过的帖子列表")
    public ApiResponse<List<PostVO>> getLikedPosts(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<PostVO> result = postService.getLikedPosts(userId, page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @GetMapping("/tags/{tagId}")
    @Operation(summary = "标签帖子列表", description = "获取指定标签下的帖子列表")
    public ApiResponse<List<PostVO>> getPostsByTag(
            @Parameter(description = "标签ID", required = true) @PathVariable Long tagId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "当前用户ID") @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        Page<PostVO> result = postService.getPostsByTag(tagId, page, size, userId);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "点赞帖子", description = "用户点赞帖子")
    @SentinelResource(value = "POST_LIKE", blockHandler = "simpleBlockHandler")
    public ApiResponse<Void> likePost(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId) {
        postService.likePost(userId, postId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/like")
    @Operation(summary = "取消点赞", description = "用户取消帖子点赞")
    public ApiResponse<Void> unlikePost(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId) {
        postService.unlikePost(userId, postId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/collect")
    @Operation(summary = "收藏帖子", description = "用户收藏帖子")
    public ApiResponse<Void> collectPost(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId) {
        postService.collectPost(userId, postId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/collect")
    @Operation(summary = "取消收藏", description = "用户取消帖子收藏")
    public ApiResponse<Void> uncollectPost(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId) {
        postService.uncollectPost(userId, postId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "分享帖子", description = "用户分享帖子，记录分享渠道并增加分享计数")
    public ApiResponse<PostShareVO> sharePost(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId,
            @Parameter(description = "分享渠道: LINK/WECHAT/WEIBO/QQ/DOUBAN") @RequestParam(defaultValue = "LINK") String channel) {
        PostShareVO vo = postShareService.sharePost(userId, postId, channel);
        return ApiResponse.ok(vo);
    }

    @GetMapping("/{id}/shares")
    @Operation(summary = "帖子分享记录", description = "获取帖子的分享记录列表")
    public ApiResponse<List<PostShareVO>> getPostShares(
            @Parameter(description = "帖子ID", required = true) @PathVariable("id") Long postId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        List<PostShareVO> shares = postShareService.getPostShares(postId, page, size);
        return ApiResponse.ok(shares);
    }

    public ApiResponse<PostVO> createPostBlockHandler(Long userId, CreatePostRequest request, BlockException ex) {
        return ApiResponse.fail("RATE_LIMITED", "发布过于频繁，请稍后再试");
    }

    public ApiResponse<List<PostVO>> feedBlockHandler(String tab, int page, int size, Long userId, BlockException ex) {
        return ApiResponse.fail("RATE_LIMITED", "请求过于频繁，请稍后再试");
    }

    public ApiResponse<List<PostVO>> searchBlockHandler(String keyword, int page, int size, Long userId, BlockException ex) {
        return ApiResponse.fail("RATE_LIMITED", "搜索过于频繁，请稍后再试");
    }

    public ApiResponse<Void> simpleBlockHandler(Long userId, Long postId, BlockException ex) {
        return ApiResponse.fail("RATE_LIMITED", "操作过于频繁，请稍后再试");
    }
}
