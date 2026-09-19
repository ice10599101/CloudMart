package com.cloudmart.notification.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.notification.service.ChatUnreadQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向内部微服务的私信未读端点（mall-pet Feign 调用：私信提醒触发）。
 * 安全：{@code hasRole('INTERNAL')}——外部请求 403。
 */
@RestController
@RequestMapping("/internal/chat")
@Tag(name = "私信·内部端点", description = "mall-pet 专用：私信未读统计（外部不可达）")
@RequiredArgsConstructor
public class InternalChatController {

    private final ChatUnreadQueryService chatUnreadQueryService;

    @GetMapping("/unread-count")
    @Operation(summary = "私信未读数", description = "用户全部会话的未读私信总数（user1/user2 未读字段求和）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Long> unreadCount(@RequestParam("userId") Long userId) {
        return ApiResponse.ok(chatUnreadQueryService.countUnreadChatMessages(userId));
    }
}
