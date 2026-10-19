package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.service.SoftwareUpdateFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * UX05：订阅更新流（登录用户本人）。游标分页；已读幂等；
 * 阅读状态只影响未读展示，不影响投递事实。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "小答软件-更新流", description = "UX05 我的订阅更新（游标分页/已读/未读数）")
public class SoftwareUpdateFeedController {

    private final SoftwareUpdateFeedService updateFeedService;

    @GetMapping("/subscriptions/mine")
    @Operation(summary = "我的订阅列表", description = "含偏好与未读数；退订软件不出现但历史更新保留")
    public ApiResponse<List<Map<String, Object>>> mySubscriptions(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(updateFeedService.mySubscriptions(userId));
    }

    @GetMapping("/updates/mine")
    @Operation(summary = "更新流（游标分页）",
            description = "cursor=上一页 nextCursor（缺省从头）；size≤30；每条含变更摘要与软件当前可见性")
    public ApiResponse<Map<String, Object>> myUpdates(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(value = "cursor", defaultValue = "0") long cursor,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(updateFeedService.myUpdates(userId, cursor, size));
    }

    @PostMapping("/updates/read")
    @Operation(summary = "批量标记已读", description = "≤50 条；重复标记幂等；只影响未读展示")
    public ApiResponse<Integer> markRead(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> eventIds = body == null ? List.of() : body.get("eventIds");
        return ApiResponse.ok(updateFeedService.markRead(userId, eventIds));
    }

    @GetMapping("/updates/unread-count")
    @Operation(summary = "未读数", description = "仅统计 SENT 投递中未读条数")
    public ApiResponse<Long> unreadCount(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(updateFeedService.unreadCount(userId));
    }
}
