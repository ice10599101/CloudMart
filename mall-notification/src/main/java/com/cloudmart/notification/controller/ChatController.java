package com.cloudmart.notification.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.notification.dto.ConversationDTO;
import com.cloudmart.notification.dto.CreateConversationRequest;
import com.cloudmart.notification.dto.MessageDTO;
import com.cloudmart.notification.dto.SendMessageRequest;
import com.cloudmart.notification.service.ChatService;
import com.cloudmart.notification.service.impl.ChatServiceImpl;
import com.cloudmart.notification.vo.ConversationVO;
import com.cloudmart.notification.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/conversations")
@Tag(name = "私信管理", description = "私信会话与消息接口")
public class ChatController {

    private final ChatService chatService;
    private final ChatServiceImpl chatServiceImpl;

    public ChatController(ChatService chatService, ChatServiceImpl chatServiceImpl) {
        this.chatService = chatService;
        this.chatServiceImpl = chatServiceImpl;
    }

    @GetMapping
    @Operation(summary = "会话列表", description = "获取当前用户的所有私信会话")
    public ApiResponse<List<ConversationVO>> listConversations(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        List<ConversationDTO> dtos = chatService.listConversations(userId);

        Set<Long> userIds = dtos.stream()
                .map(ConversationDTO::otherUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ChatServiceImpl.UserInfo> userInfoMap = chatServiceImpl.batchGetUsers(userIds);

        List<ConversationVO> vos = dtos.stream()
                .map(dto -> {
                    ChatServiceImpl.UserInfo otherUser = userInfoMap.get(dto.otherUserId());
                    return new ConversationVO(
                            dto.id(),
                            dto.otherUserId(),
                            otherUser != null ? otherUser.nickname() : "用户" + dto.otherUserId(),
                            otherUser != null ? otherUser.avatar() : "",
                            dto.lastMessage(),
                            dto.lastMessageTime(),
                            dto.unreadCount()
                    );
                })
                .toList();

        return ApiResponse.ok(vos);
    }

    @PostMapping
    @Operation(summary = "创建会话", description = "与指定用户创建私信会话，如已存在则返回已有会话")
    public ApiResponse<ConversationVO> createConversation(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CreateConversationRequest request) {
        ConversationDTO dto = chatService.createConversation(userId, request.otherUserId());

        Map<Long, ChatServiceImpl.UserInfo> userInfoMap =
                chatServiceImpl.batchGetUsers(Set.of(dto.otherUserId()));
        ChatServiceImpl.UserInfo otherUser = userInfoMap.get(dto.otherUserId());

        ConversationVO vo = new ConversationVO(
                dto.id(),
                dto.otherUserId(),
                otherUser != null ? otherUser.nickname() : "用户" + dto.otherUserId(),
                otherUser != null ? otherUser.avatar() : "",
                dto.lastMessage(),
                dto.lastMessageTime(),
                dto.unreadCount()
        );

        return ApiResponse.ok(vo);
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "消息列表", description = "获取指定会话的消息记录，支持分页")
    public ApiResponse<List<MessageVO>> listMessages(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "会话ID") @PathVariable("conversationId") Long conversationId,
            @Parameter(description = "起始消息ID(加载更早消息)") @RequestParam(value = "beforeId", required = false) Long beforeId,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "30") Integer pageSize) {
        List<MessageDTO> dtos = chatService.listMessages(userId, conversationId, beforeId, pageSize);

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

    @PostMapping("/{conversationId}/messages")
    @Operation(summary = "发送消息", description = "在指定会话中发送消息")
    public ApiResponse<MessageVO> sendMessage(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "会话ID") @PathVariable("conversationId") Long conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        MessageDTO dto = chatService.sendMessage(userId, conversationId, request.content(), request.type());

        Map<Long, ChatServiceImpl.UserInfo> userInfoMap =
                chatServiceImpl.batchGetUsers(Set.of(dto.senderId()));
        ChatServiceImpl.UserInfo sender = userInfoMap.get(dto.senderId());

        MessageVO vo = new MessageVO(
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

        return ApiResponse.ok(vo);
    }

    @PutMapping("/{conversationId}/read")
    @Operation(summary = "标记已读", description = "标记指定会话的所有消息为已读")
    public ApiResponse<Void> markConversationRead(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "会话ID") @PathVariable("conversationId") Long conversationId) {
        chatService.markConversationRead(userId, conversationId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/messages/{messageId}/recall")
    @Operation(summary = "撤回消息", description = "撤回自己发送的消息，2分钟内有效")
    public ApiResponse<MessageVO> recallMessage(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "消息ID") @PathVariable("messageId") Long messageId) {
        MessageDTO dto = chatService.recallMessage(userId, messageId);

        Map<Long, ChatServiceImpl.UserInfo> userInfoMap =
                chatServiceImpl.batchGetUsers(Set.of(dto.senderId()));
        ChatServiceImpl.UserInfo sender = userInfoMap.get(dto.senderId());

        MessageVO vo = new MessageVO(
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

        return ApiResponse.ok(vo);
    }
}
