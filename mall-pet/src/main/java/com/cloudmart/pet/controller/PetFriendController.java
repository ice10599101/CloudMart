package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetFriendService;
import com.cloudmart.pet.vo.PetFriendPanelVO;
import com.cloudmart.pet.vo.PetFriendVO;
import com.cloudmart.pet.vo.PetFriendVisitResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物好友与互访接口（三期）。
 *
 * <p>好友是双向关系（确认时双方各落一行），互访复用家园的房间结算并叠加好友层收益。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物好友", description = "好友列表/申请/确认/删除/互访")
@RequiredArgsConstructor
public class PetFriendController {

    private final PetFriendService friendService;
    private final com.cloudmart.pet.service.impl.PetFriendFeedService friendFeedService;

    @GetMapping("/friends/feed")
    @Operation(summary = "好友动态（F3）", description = "收件箱游标分页（beforeId=上一页最后一条）；升级/打工读书完成/对战获胜事件")
    @SentinelResource("PET_QUERY")
    public ApiResponse<java.util.List<com.cloudmart.pet.service.impl.PetFriendFeedService.FeedItemVO>> feed(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "游标：上一页最后一条 feed id") @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return ApiResponse.ok(friendFeedService.list(userId, beforeId, size != null ? size : 20));
    }

    @GetMapping("/friends/feed/unread-count")
    @Operation(summary = "好友动态未读数（F3）", description = "已读水位之后的条数（封顶 100）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<Long> feedUnread(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(friendFeedService.unreadCount(userId));
    }

    @PostMapping("/friends/feed/read")
    @Operation(summary = "标记好友动态已读（F3）", description = "水位推进到当前最大 id（幂等，只前进）")
    @SentinelResource("PET_SOCIAL_UPDATE")
    public ApiResponse<Void> feedRead(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        friendFeedService.markRead(userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/friends")
    @Operation(summary = "好友面板", description = "好友 + 收到申请 + 我发出的申请 + 今日互访余量")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetFriendPanelVO> friends(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(friendService.panel(userId));
    }

    @PostMapping("/friends/{userId}")
    @Operation(summary = "申请加好友", description = "对方已向我申请时直接互相确认；重复申请 409 PET_FRIEND_EXISTS")
    @SentinelResource("PET_FRIEND")
    public ApiResponse<PetFriendVO> request(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("userId") Long friendUserId) {
        return ApiResponse.ok(friendService.request(userId, friendUserId));
    }

    @PostMapping("/friends/{userId}/accept")
    @Operation(summary = "同意好友申请")
    @SentinelResource("PET_FRIEND")
    public ApiResponse<PetFriendVO> accept(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("userId") Long friendUserId) {
        return ApiResponse.ok(friendService.accept(userId, friendUserId));
    }

    @PostMapping("/friends/{userId}/reject")
    @Operation(summary = "拒绝好友申请")
    @SentinelResource("PET_FRIEND")
    public ApiResponse<PetFriendVO> reject(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("userId") Long friendUserId) {
        return ApiResponse.ok(friendService.reject(userId, friendUserId));
    }

    @DeleteMapping("/friends/{userId}")
    @Operation(summary = "删除好友", description = "双向解除")
    @SentinelResource("PET_FRIEND")
    public ApiResponse<Void> remove(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("userId") Long friendUserId) {
        friendService.remove(userId, friendUserId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/friends/{userId}/visit")
    @Operation(summary = "好友互访", description = "每日次数上限；双方受益 + 关系亲密度 + 每日任务进度")
    @SentinelResource("PET_VISIT")
    public ApiResponse<PetFriendVisitResultVO> visit(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("userId") Long friendUserId) {
        return ApiResponse.ok(friendService.visit(userId, friendUserId));
    }
}
