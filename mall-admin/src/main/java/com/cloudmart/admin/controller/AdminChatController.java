package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.ChatFeignClient;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "聊天管理", description = "管理后台聊天模块代理接口")
@RequiredArgsConstructor
public class AdminChatController {

    private final ChatFeignClient chatFeignClient;

    @GetMapping("/chat/conversations")
    @Operation(summary = "会话列表")
    public ApiResponse<List<Map<String, Object>>> listConversations(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return chatFeignClient.listConversations(page, pageSize);
    }

    @GetMapping("/chat/conversations/{conversationId}/messages")
    @Operation(summary = "会话消息")
    public ApiResponse<List<Map<String, Object>>> listMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return chatFeignClient.listMessages(conversationId, page, pageSize);
    }

    @GetMapping("/chat/stats")
    @Operation(summary = "聊天统计")
    public ApiResponse<Map<String, Long>> getChatStats() {
        return chatFeignClient.getChatStats();
    }
}
