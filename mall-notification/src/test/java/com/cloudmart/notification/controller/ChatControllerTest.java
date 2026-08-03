package com.cloudmart.notification.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.notification.dto.ConversationDTO;
import com.cloudmart.notification.dto.MessageDTO;
import com.cloudmart.notification.service.ChatService;
import com.cloudmart.notification.service.impl.ChatServiceImpl;
import com.cloudmart.notification.vo.ConversationVO;
import com.cloudmart.notification.vo.MessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private MockMvc mockMvc;

    private final ChatService chatService = Mockito.mock(ChatService.class);
    private final ChatServiceImpl chatServiceImpl = Mockito.mock(ChatServiceImpl.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService, chatServiceImpl))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("会话列表 - 成功返回信封")
    void listConversations_ShouldReturnEnvelope() throws Exception {
        ConversationDTO dto = new ConversationDTO(1L, 1L, 2L, "你好",
                FIXED_TIME, 3, 2L, FIXED_TIME);
        ChatServiceImpl.UserInfo otherUser = new ChatServiceImpl.UserInfo(2L, "用户B", "avatar.jpg");

        given(chatService.listConversations(1L)).willReturn(List.of(dto));
        given(chatServiceImpl.batchGetUsers(Set.of(2L))).willReturn(Map.of(2L, otherUser));

        mockMvc.perform(get("/conversations")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].otherUserNickname").value("用户B"));
    }

    @Test
    @DisplayName("创建会话 - 成功返回信封")
    void createConversation_ShouldReturnEnvelope() throws Exception {
        ConversationDTO dto = new ConversationDTO(1L, 1L, 2L, null,
                null, 0, 2L, FIXED_TIME);
        ChatServiceImpl.UserInfo otherUser = new ChatServiceImpl.UserInfo(2L, "用户B", "avatar.jpg");

        given(chatService.createConversation(1L, 2L)).willReturn(dto);
        given(chatServiceImpl.batchGetUsers(Set.of(2L))).willReturn(Map.of(2L, otherUser));

        mockMvc.perform(post("/conversations")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otherUserId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.otherUserNickname").value("用户B"));
    }

    @Test
    @DisplayName("消息列表 - 成功返回信封")
    void listMessages_ShouldReturnEnvelope() throws Exception {
        MessageDTO dto = new MessageDTO(10L, 1L, 2L, "你好", "TEXT", false, FIXED_TIME);
        ChatServiceImpl.UserInfo sender = new ChatServiceImpl.UserInfo(2L, "用户B", "avatar.jpg");

        given(chatService.listMessages(1L, 1L, null, 30)).willReturn(List.of(dto));
        given(chatServiceImpl.batchGetUsers(Set.of(2L))).willReturn(Map.of(2L, sender));

        mockMvc.perform(get("/conversations/1/messages")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].content").value("你好"));
    }

    @Test
    @DisplayName("发送消息 - 成功返回信封")
    void sendMessage_ShouldReturnEnvelope() throws Exception {
        MessageDTO dto = new MessageDTO(10L, 1L, 1L, "你好", "TEXT", false, FIXED_TIME);
        ChatServiceImpl.UserInfo sender = new ChatServiceImpl.UserInfo(1L, "用户A", "avatar.jpg");

        given(chatService.sendMessage(1L, 1L, "你好", "TEXT")).willReturn(dto);
        given(chatServiceImpl.batchGetUsers(Set.of(1L))).willReturn(Map.of(1L, sender));

        mockMvc.perform(post("/conversations/1/messages")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\",\"type\":\"TEXT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.content").value("你好"));
    }

    @Test
    @DisplayName("标记会话已读 - 成功返回信封")
    void markConversationRead_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(put("/conversations/1/read")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(chatService).markConversationRead(1L, 1L);
    }

    @Test
    @DisplayName("撤回消息 - 成功返回信封")
    void recallMessage_ShouldReturnEnvelope() throws Exception {
        MessageDTO dto = new MessageDTO(10L, 1L, 1L, "你好", "TEXT", true, FIXED_TIME);
        ChatServiceImpl.UserInfo sender = new ChatServiceImpl.UserInfo(1L, "用户A", "avatar.jpg");

        given(chatService.recallMessage(1L, 10L)).willReturn(dto);
        given(chatServiceImpl.batchGetUsers(Set.of(1L))).willReturn(Map.of(1L, sender));

        mockMvc.perform(put("/conversations/messages/10/recall")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isRecalled").value(true));
    }
}
