package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.dto.ChatRequest;
import com.cloudmart.ai.dto.ChatResponse;
import com.cloudmart.ai.dto.VectorSearchResult;
import com.cloudmart.ai.service.VectorSearchService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceImplTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private VectorSearchService vectorSearchService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private org.springframework.data.redis.core.ListOperations<String, String> listOperations;

    private ObjectMapper objectMapper;

    private AiChatServiceImpl aiChatService;

    private static final Long USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiChatService = new AiChatServiceImpl(
                chatClientBuilder, vectorSearchService, redisTemplate, objectMapper
        );
        // AI-01：历史存储为 Redis List（按 userId 命名空间）；默认空历史
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(anyString(), anyLong(), anyLong())).thenReturn(null);
    }

    @Nested
    @DisplayName("chat")
    class ChatTests {

        @Test
        @DisplayName("should return LLM response successfully")
        void chat_success_returnsLlmResponse() {
            ChatRequest request = new ChatRequest("推荐一款手机", null);
            when(vectorSearchService.semanticSearch(anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

            when(chatClient.prompt()).thenReturn(requestSpec);
            doReturn(requestSpec).when(requestSpec).messages(any(List.class));
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("我推荐您看看 iPhone 16，性价比很高！");

            ChatResponse response = aiChatService.chat(USER_ID, request);

            assertThat(response.reply()).isEqualTo("我推荐您看看 iPhone 16，性价比很高！");
            assertThat(response.degraded()).isFalse();
            assertThat(response.conversationId()).isNotBlank();
        }

        @Test
        @DisplayName("should degrade to keyword search when LLM is unavailable")
        void chat_llmUnavailable_degradesToKeywordSearch() {
            ChatRequest request = new ChatRequest("推荐一款手机", null);
            when(vectorSearchService.semanticSearch(anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            when(chatClient.prompt()).thenReturn(requestSpec);
            doReturn(requestSpec).when(requestSpec).messages(any(List.class));
            when(requestSpec.call()).thenThrow(new RuntimeException("LLM service unavailable"));

            ChatResponse response = aiChatService.chat(USER_ID, request);

            assertThat(response.degraded()).isTrue();
            assertThat(response.reply()).contains("搜索功能");
        }

        @Test
        @DisplayName("should include RAG context when vector search returns results")
        void chat_withRagContext_includesSearchResults() {
            VectorSearchResult searchResult = new VectorSearchResult(
                    1L, "iPhone 16", "最新款苹果手机", new BigDecimal("6999"),
                    "http://img.test.com/iphone16.jpg", "手机", 0.95
            );
            ChatRequest request = new ChatRequest("推荐一款手机", null);
            when(vectorSearchService.semanticSearch(anyString(), anyInt()))
                    .thenReturn(List.of(searchResult));

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

            when(chatClient.prompt()).thenReturn(requestSpec);
            doReturn(requestSpec).when(requestSpec).messages(any(List.class));
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("基于搜索结果推荐 iPhone 16");

            ChatResponse response = aiChatService.chat(USER_ID, request);

            assertThat(response.reply()).isEqualTo("基于搜索结果推荐 iPhone 16");
        }

        @Test
        @DisplayName("AI-01：回传本服务签发的 UUID 会话 ID 时继续同一会话（键绑定 userId 命名空间）")
        void chat_existingConversation_continuesConversation() {
            String existingConvId = "0199f225-0000-7000-8000-000000000001";
            ChatRequest request = new ChatRequest("还有别的推荐吗", existingConvId);

            when(listOperations.range("ai:conversation:1001:" + existingConvId, 0, -1))
                    .thenReturn(List.of(
                            "{\"role\":\"user\",\"content\":\"推荐一款手机\"}",
                            "{\"role\":\"assistant\",\"content\":\"我推荐 iPhone 16\"}"));
            when(vectorSearchService.semanticSearch(anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

            when(chatClient.prompt()).thenReturn(requestSpec);
            doReturn(requestSpec).when(requestSpec).messages(any(List.class));
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("还有 Samsung Galaxy S25 也不错");

            ChatResponse response = aiChatService.chat(USER_ID, request);

            assertThat(response.conversationId()).isEqualTo(existingConvId);
            assertThat(response.reply()).isEqualTo("还有 Samsung Galaxy S25 也不错");
        }

        @Test
        @DisplayName("AI-01：旧格式/自造会话 ID 被拒绝并签发新会话（旧会话无可靠 owner，失效重建）")
        void chat_legacyOrForgedConversationId_issuesNewConversation() {
            ChatRequest request = new ChatRequest("还有别的推荐吗", "conv:1001:1234567890");
            when(vectorSearchService.semanticSearch(anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

            when(chatClient.prompt()).thenReturn(requestSpec);
            doReturn(requestSpec).when(requestSpec).messages(any(List.class));
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("推荐 iPhone 16");

            ChatResponse response = aiChatService.chat(USER_ID, request);

            // 新 UUID 与旧 ID 不同；历史读取与追加都走新会话键
            assertThat(response.conversationId()).isNotEqualTo(request.conversationId());
            verify(listOperations).range(
                    org.mockito.ArgumentMatchers.eq("ai:conversation:1001:" + response.conversationId()),
                    org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.eq(-1L));
        }

        @Test
        @DisplayName("AI-01：含结构字符的会话 ID（键注入尝试）不接受，签发新会话")
        void chat_keyInjectionConversationId_rejected() {
            ChatRequest request = new ChatRequest("hi", "1001:injected");
            when(vectorSearchService.semanticSearch(anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

            when(chatClient.prompt()).thenReturn(requestSpec);
            doReturn(requestSpec).when(requestSpec).messages(any(List.class));
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("你好");

            ChatResponse response = aiChatService.chat(USER_ID, request);

            assertThat(response.conversationId()).isNotEqualTo(request.conversationId());
            assertThat(response.conversationId()).doesNotContain(":");
        }

        @Test
        @DisplayName("AI-01：本轮对话原子追加到 userId 命名空间键（并发消息不丢）")
        void chat_appendsHistoryToUserNamespacedKey() {
            ChatRequest request = new ChatRequest("推荐一款手机", null);
            when(vectorSearchService.semanticSearch(anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

            when(chatClient.prompt()).thenReturn(requestSpec);
            doReturn(requestSpec).when(requestSpec).messages(any(List.class));
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("推荐 iPhone 16");

            ChatResponse response = aiChatService.chat(USER_ID, request);

            org.mockito.ArgumentCaptor<String> keyCaptor =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.ArgumentCaptor<String[]> valuesCaptor =
                    org.mockito.ArgumentCaptor.forClass(String[].class);
            verify(listOperations).rightPushAll(keyCaptor.capture(), valuesCaptor.capture());
            assertThat(keyCaptor.getValue())
                    .isEqualTo("ai:conversation:1001:" + response.conversationId());
            assertThat(valuesCaptor.getValue()).hasSize(2);
            verify(redisTemplate).expire(keyCaptor.getValue(), java.time.Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("should handle RAG search failure gracefully")
        void chat_ragSearchFails_continuesWithoutRag() {
            ChatRequest request = new ChatRequest("推荐一款手机", null);
            when(vectorSearchService.semanticSearch(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("ES unavailable"));

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

            when(chatClient.prompt()).thenReturn(requestSpec);
            doReturn(requestSpec).when(requestSpec).messages(any(List.class));
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("推荐您看看我们的热销商品");

            ChatResponse response = aiChatService.chat(USER_ID, request);

            assertThat(response.reply()).isEqualTo("推荐您看看我们的热销商品");
            assertThat(response.degraded()).isFalse();
        }
    }
}
