package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.service.UserBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/blocks")
@Tag(name = "用户拉黑", description = "用户拉黑/屏蔽功能接口")
@RequiredArgsConstructor
public class UserBlockController {

    private final UserBlockService userBlockService;

    @PostMapping("/{userId}")
    @Operation(summary = "拉黑用户", description = "拉黑指定用户")
    public ApiResponse<Void> blockUser(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long currentUserId,
            @Parameter(description = "目标用户ID") @PathVariable("userId") Long blockedUserId) {
        userBlockService.blockUser(currentUserId, blockedUserId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "取消拉黑", description = "取消拉黑指定用户")
    public ApiResponse<Void> unblockUser(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long currentUserId,
            @Parameter(description = "目标用户ID") @PathVariable("userId") Long blockedUserId) {
        userBlockService.unblockUser(currentUserId, blockedUserId);
        return ApiResponse.ok(null);
    }

    @GetMapping
    @Operation(summary = "拉黑列表", description = "获取当前用户的拉黑列表")
    public ApiResponse<List<Long>> getBlockedUsers(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long currentUserId) {
        List<Long> blockedIds = userBlockService.getBlockedUserIds(currentUserId);
        return ApiResponse.ok(blockedIds);
    }

    @GetMapping("/check")
    @Operation(summary = "检查拉黑状态", description = "检查是否拉黑了指定用户")
    public ApiResponse<Map<String, Boolean>> checkBlockStatus(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long currentUserId,
            @Parameter(description = "目标用户ID") @RequestParam("targetUserId") Long targetUserId) {
        boolean blocked = userBlockService.isBlocked(currentUserId, targetUserId);
        return ApiResponse.ok(Map.of("blocked", blocked));
    }
}
