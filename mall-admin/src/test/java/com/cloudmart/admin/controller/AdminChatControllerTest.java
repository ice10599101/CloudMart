package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.ChatFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminChatControllerTest {

    private MockMvc mockMvc;
    private ChatFeignClient chatFeignClient;

    @BeforeEach
    void setUp() {
        chatFeignClient = mock(ChatFeignClient.class);
        AdminChatController controller = new AdminChatController(chatFeignClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /chat/conversations - 会话列表")
    class ListConversationsTests {

        @Test
        @DisplayName("返回会话列表")
        void listConversations_returnsConversationList() throws Exception {
            List<Map<String, Object>> conversations = List.of(
                    Map.of("id", 1, "title", "Test Chat")
            );
            given(chatFeignClient.listConversations(1, 20)).willReturn(ApiResponse.ok(conversations));

            mockMvc.perform(get("/chat/conversations")
                            .param("page", "1")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].title").value("Test Chat"));

            verify(chatFeignClient).listConversations(1, 20);
        }
    }

    @Nested
    @DisplayName("GET /chat/conversations/{conversationId}/messages - 会话消息")
    class ListMessagesTests {

        @Test
        @DisplayName("返回指定会话的消息列表")
        void listMessages_returnsMessageList() throws Exception {
            List<Map<String, Object>> messages = List.of(
                    Map.of("id", 1, "content", "Hello")
            );
            given(chatFeignClient.listMessages(1L, 1, 20)).willReturn(ApiResponse.ok(messages));

            mockMvc.perform(get("/chat/conversations/1/messages")
                            .param("page", "1")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].content").value("Hello"));

            verify(chatFeignClient).listMessages(1L, 1, 20);
        }
    }

    @Nested
    @DisplayName("GET /chat/stats - 聊天统计")
    class GetChatStatsTests {

        @Test
        @DisplayName("返回聊天统计数据")
        void getChatStats_returnsStats() throws Exception {
            Map<String, Long> stats = Map.of(
                    "totalConversations", 100L,
                    "totalMessages", 5000L
            );
            given(chatFeignClient.getChatStats()).willReturn(ApiResponse.ok(stats));

            mockMvc.perform(get("/chat/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalConversations").value(100));

            verify(chatFeignClient).getChatStats();
        }
    }
}
