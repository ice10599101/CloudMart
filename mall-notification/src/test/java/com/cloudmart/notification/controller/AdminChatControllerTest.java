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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminChatControllerTest {

    private MockMvc mockMvc;

    private final ChatService chatService = Mockito.mock(ChatService.class);
    private final ChatServiceImpl chatServiceImpl = Mockito.mock(ChatServiceImpl.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminChatController(chatService, chatServiceImpl))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("会话列表 - 成功返回信封")
    void listConversations_ShouldReturnEnvelope() throws Exception {
        ConversationDTO dto = new ConversationDTO(1L, 1L, 2L, "你好",
                FIXED_TIME, 3, 2L, FIXED_TIME);
        ChatServiceImpl.UserInfo user1 = new ChatServiceImpl.UserInfo(1L, "用户A", "avatar1.jpg");
        ChatServiceImpl.UserInfo user2 = new ChatServiceImpl.UserInfo(2L, "用户B", "avatar2.jpg");

        given(chatService.listAllConversations(1, 20)).willReturn(List.of(dto));
        given(chatServiceImpl.batchGetUsers(Set.of(1L, 2L))).willReturn(Map.of(1L, user1, 2L, user2));

        mockMvc.perform(get("/admin/chat/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("会话消息 - 成功返回信封")
    void listMessages_ShouldReturnEnvelope() throws Exception {
        MessageDTO dto = new MessageDTO(10L, 1L, 1L, "你好", "TEXT", false, FIXED_TIME);
        ChatServiceImpl.UserInfo sender = new ChatServiceImpl.UserInfo(1L, "用户A", "avatar1.jpg");

        given(chatService.listAllMessages(1L, 1, 20)).willReturn(List.of(dto));
        given(chatServiceImpl.batchGetUsers(Set.of(1L))).willReturn(Map.of(1L, sender));

        mockMvc.perform(get("/admin/chat/conversations/1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].content").value("你好"));
    }

    @Test
    @DisplayName("聊天统计 - 成功返回信封")
    void getChatStats_ShouldReturnEnvelope() throws Exception {
        given(chatService.getConversationCount()).willReturn(100L);
        given(chatService.getMessageCount()).willReturn(5000L);

        mockMvc.perform(get("/admin/chat/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationCount").value(100))
                .andExpect(jsonPath("$.data.messageCount").value(5000));
    }
}
