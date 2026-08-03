package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.service.TagSubscriptionService;
import com.cloudmart.community.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tags/subscriptions")
@Tag(name = "话题订阅", description = "话题订阅/取消订阅/查询接口")
@RequiredArgsConstructor
public class TagSubscriptionController {

    private final TagSubscriptionService tagSubscriptionService;

    @PostMapping("/{tagId}")
    @Operation(summary = "订阅话题", description = "用户订阅指定话题")
    public ApiResponse<Void> subscribe(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "话题ID", required = true) @PathVariable Long tagId) {
        tagSubscriptionService.subscribe(userId, tagId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{tagId}")
    @Operation(summary = "取消订阅", description = "用户取消订阅指定话题")
    public ApiResponse<Void> unsubscribe(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "话题ID", required = true) @PathVariable Long tagId) {
        tagSubscriptionService.unsubscribe(userId, tagId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{tagId}/status")
    @Operation(summary = "订阅状态", description = "查询当前用户是否已订阅指定话题")
    public ApiResponse<Boolean> checkSubscription(
            @Parameter(description = "当前用户ID") @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId,
            @Parameter(description = "话题ID", required = true) @PathVariable Long tagId) {
        boolean subscribed = tagSubscriptionService.isSubscribed(userId, tagId);
        return ApiResponse.ok(subscribed);
    }

    @GetMapping
    @Operation(summary = "已订阅话题列表", description = "获取当前用户已订阅的话题列表")
    public ApiResponse<List<TagVO>> getSubscribedTags(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        List<TagVO> tags = tagSubscriptionService.getSubscribedTags(userId);
        return ApiResponse.ok(tags);
    }
}
