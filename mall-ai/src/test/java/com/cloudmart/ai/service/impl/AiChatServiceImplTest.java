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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
    }

    @Nested
    @DisplayName("chat")
    class ChatTests {

        @Test
        @DisplayName("should return LLM response successfully")
        void chat_success_returnsLlmResponse() {
            ChatRequest request = new ChatRequest("推荐一款手机", null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
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
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
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
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
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
        @DisplayName("should continue existing conversation when conversationId provided")
        void chat_existingConversation_continuesConversation() {
            String existingConvId = "conv:1001:1234567890";
            ChatRequest request = new ChatRequest("还有别的推荐吗", existingConvId);

            String historyJson = "[{\"role\":\"system\",\"content\":\"You are a helpful assistant\"}," +
                    "{\"role\":\"user\",\"content\":\"推荐一款手机\"}," +
                    "{\"role\":\"assistant\",\"content\":\"我推荐 iPhone 16\"}]";
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("ai:conversation:" + existingConvId)).thenReturn(historyJson);
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
        @DisplayName("should handle RAG search failure gracefully")
        void chat_ragSearchFails_continuesWithoutRag() {
            ChatRequest request = new ChatRequest("推荐一款手机", null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
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
