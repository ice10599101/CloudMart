package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.service.PostService;
import com.cloudmart.community.service.UserCommunityService;
import com.cloudmart.community.service.UserFollowService;
import com.cloudmart.community.vo.PostVO;
import com.cloudmart.community.vo.UserCommunityVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@Tag(name = "社区用户", description = "社区用户资料和关注接口")
@RequiredArgsConstructor
public class UserCommunityController {

    private final UserCommunityService userCommunityService;
    private final UserFollowService userFollowService;
    private final PostService postService;
    private final com.cloudmart.community.service.UserEnrichmentService userEnrichmentService;

    /**
     * 用户搜索（私信发起会话等场景）。
     * 必须声明在 /{userId} 类路径段之前，避免 "search" 被当作 userId 解析。
     */
    @GetMapping("/search")
    @Operation(summary = "搜索用户", description = "按昵称关键词搜索用户，用于发起私信等场景")
    public ApiResponse<List<Map<String, Object>>> searchUsers(
            @Parameter(description = "昵称/小答号关键词", required = true) @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        var response = userEnrichmentService.searchUsersByKeyword(keyword, page, pageSize);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{userId}/profile")
    @Operation(summary = "用户社区资料", description = "获取指定用户的社区资料，含发帖数、关注数、粉丝数、徽章等")
    public ApiResponse<UserCommunityVO> getUserProfile(
            @Parameter(description = "目标用户ID", required = true) @PathVariable Long userId,
            @Parameter(description = "当前用户ID") @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long currentUserId) {
        UserCommunityVO vo = userCommunityService.getUserProfile(userId, currentUserId);
        return ApiResponse.ok(vo);
    }

    @PostMapping("/{userId}/follow")
    @Operation(summary = "关注用户", description = "当前用户关注目标用户")
    public ApiResponse<Void> follow(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long currentUserId,
            @Parameter(description = "目标用户ID", required = true) @PathVariable Long userId) {
        userFollowService.follow(currentUserId, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{userId}/follow")
    @Operation(summary = "取消关注", description = "当前用户取消关注目标用户")
    public ApiResponse<Void> unfollow(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long currentUserId,
            @Parameter(description = "目标用户ID", required = true) @PathVariable Long userId) {
        userFollowService.unfollow(currentUserId, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{userId}/collections")
    @Operation(summary = "用户收藏列表", description = "获取用户收藏的帖子列表")
    public ApiResponse<List<PostVO>> getUserCollections(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long currentUserId) {
        Page<PostVO> result = postService.getUserCollections(userId, page, size, currentUserId);
        return ApiResponse.ok(result.getRecords(), new ApiResponse.Meta(page, size, result.getTotal()));
    }

    @GetMapping("/{userId}/followers")
    @Operation(summary = "粉丝列表", description = "获取用户的粉丝列表")
    public ApiResponse<List<UserCommunityVO>> getFollowers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long currentUserId) {
        List<UserCommunityVO> result = userFollowService.getFollowerList(userId, currentUserId, page, size);
        return ApiResponse.ok(result);
    }

    @GetMapping("/{userId}/following")
    @Operation(summary = "关注列表", description = "获取用户关注的人列表")
    public ApiResponse<List<UserCommunityVO>> getFollowingList(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long currentUserId) {
        List<UserCommunityVO> result = userFollowService.getFollowingList(userId, currentUserId, page, size);
        return ApiResponse.ok(result);
    }

    @GetMapping("/recommend")
    @Operation(summary = "推荐用户", description = "基于共同关注和热门用户推荐可能感兴趣的人")
    public ApiResponse<List<UserCommunityVO>> getRecommendedUsers(
            @Parameter(description = "当前用户ID") @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId,
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "6") int limit) {
        List<UserCommunityVO> result = userFollowService.getRecommendedUsers(userId, limit);
        return ApiResponse.ok(result);
    }
}
