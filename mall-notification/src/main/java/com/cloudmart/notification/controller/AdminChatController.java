package com.cloudmart.notification.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.notification.dto.ConversationDTO;
import com.cloudmart.notification.dto.MessageDTO;
import com.cloudmart.notification.service.ChatService;
import com.cloudmart.notification.service.impl.ChatServiceImpl;
import com.cloudmart.notification.vo.ConversationVO;
import com.cloudmart.notification.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/chat")
@Tag(name = "聊天管理(后台)", description = "管理后台聊天会话与消息管理接口")
@RequiredArgsConstructor
public class AdminChatController {

    private final ChatService chatService;
    private final ChatServiceImpl chatServiceImpl;

    @GetMapping("/conversations")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "会话列表", description = "管理后台分页查询所有会话")
    public ApiResponse<List<ConversationVO>> listConversations(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        List<ConversationDTO> dtos = chatService.listAllConversations(page, pageSize);

        Set<Long> userIds = new HashSet<>();
        for (ConversationDTO dto : dtos) {
            userIds.add(dto.user1Id());
            userIds.add(dto.user2Id());
        }
        Map<Long, ChatServiceImpl.UserInfo> userInfoMap = chatServiceImpl.batchGetUsers(userIds);

        List<ConversationVO> vos = dtos.stream()
                .map(dto -> {
                    ChatServiceImpl.UserInfo user1 = userInfoMap.get(dto.user1Id());
                    ChatServiceImpl.UserInfo user2 = userInfoMap.get(dto.user2Id());
                    String displayName = (user1 != null ? user1.nickname() : "用户" + dto.user1Id())
                            + " ↔ "
                            + (user2 != null ? user2.nickname() : "用户" + dto.user2Id());
                    return new ConversationVO(
                            dto.id(),
                            dto.otherUserId(),
                            displayName,
                            "",
                            dto.lastMessage(),
                            dto.lastMessageTime(),
                            dto.unreadCount()
                    );
                })
                .toList();

        return ApiResponse.ok(vos);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "会话消息", description = "管理后台查询指定会话的消息记录")
    public ApiResponse<List<MessageVO>> listMessages(
            @Parameter(description = "会话ID") @PathVariable("conversationId") Long conversationId,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        List<MessageDTO> dtos = chatService.listAllMessages(conversationId, page, pageSize);

        Set<Long> senderIds = dtos.stream()
                .map(MessageDTO::senderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ChatServiceImpl.UserInfo> userInfoMap = chatServiceImpl.batchGetUsers(senderIds);

        List<MessageVO> vos = dtos.stream()
                .map(dto -> {
                    ChatServiceImpl.UserInfo sender = userInfoMap.get(dto.senderId());
                    return new MessageVO(
                            dto.id(),
                            dto.conversationId(),
                            dto.senderId(),
                            sender != null ? sender.nickname() : "用户" + dto.senderId(),
                            sender != null ? sender.avatar() : "",
                            dto.content(),
                            dto.type(),
                            dto.isRecalled(),
                            dto.createdAt()
                    );
                })
                .toList();

        return ApiResponse.ok(vos);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "聊天统计", description = "管理后台获取聊天统计数据")
    public ApiResponse<Map<String, Long>> getChatStats() {
        return ApiResponse.ok(Map.of(
                "conversationCount", chatService.getConversationCount(),
                "messageCount", chatService.getMessageCount()
        ));
    }
}
