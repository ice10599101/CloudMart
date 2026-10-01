package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.ChatFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.annotation.OperLog;
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
    @RequiresPermission("chat:content:read")
    @Operation(summary = "会话列表", description = "仅元数据（会话双方/时间）；消息正文读取走独立权限")
    public ApiResponse<List<Map<String, Object>>> listConversations(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return chatFeignClient.listConversations(page, pageSize);
    }

    /**
     * S02：私聊正文读取独立权限 + 强制 caseId/reason + 审计。
     * 仅客服/审核工单流程允许读取；无工单上下文直接拒绝（禁止仅有 admin 身份即全量读私聊）。
     */
    @GetMapping("/chat/conversations/{conversationId}/messages")
    @OperLog(title = "聊天管理", businessType = 0)
    @RequiresPermission("chat:content:read")
    @Operation(summary = "会话消息", description = "强制携带 caseId 与 reason（工单上下文），读取行为入操作审计")
    public ApiResponse<List<Map<String, Object>>> listMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(value = "caseId", required = false) String caseId,
            @RequestParam(value = "reason", required = false) String reason) {
        if (caseId == null || caseId.isBlank() || reason == null || reason.isBlank()) {
            throw new BusinessException("CHAT_READ_CONTEXT_REQUIRED",
                    "读取私聊正文必须提供工单 ID 与理由");
        }
        return chatFeignClient.listMessages(conversationId, page, pageSize);
    }

    @GetMapping("/chat/stats")
    @RequiresPermission("chat:content:read")
    @Operation(summary = "聊天统计", description = "聚合计数，不含正文")
    public ApiResponse<Map<String, Long>> getChatStats() {
        return chatFeignClient.getChatStats();
    }
}