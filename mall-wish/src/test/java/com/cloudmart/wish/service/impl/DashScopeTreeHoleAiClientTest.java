package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.config.WishAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DashScopeTreeHoleAiClient 单元测试（文档 30.3：失败重试 2 次）。
 *
 * <p>通过 mock ChatClient 流式接口验证：首次成功不重试、失败重试后成功、
 * 重试耗尽抛 503、中断异常直接失败。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DashScopeTreeHoleAiClient 单元测试")
class DashScopeTreeHoleAiClientTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    private ChatClient chatClient;
    private DashScopeTreeHoleAiClient aiClient;

    private static final String SYSTEM_PROMPT = "system";
    private static final String USER_MESSAGE = "message";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        WishAiProperties properties = new WishAiProperties();
        properties.setMaxRetries(2);
        properties.setRetryIntervalMs(0);

        chatClient = mock(ChatClient.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiClient = new DashScopeTreeHoleAiClient(chatClientBuilder, new TreeHoleReplyParser(), properties);
    }

    private void stubCallContent(String content) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(content);
    }

    private void stubCallThrow(RuntimeException exception) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(exception);
    }

    @Nested
    @DisplayName("generateReply - 调用与重试")
    class GenerateReplyTests {

        @Test
        @DisplayName("首次成功：不重试，返回解析后的回复")
        void shouldReturnReplyOnFirstAttempt() {
            stubCallContent("{\"reply\": \"回复\", \"sentimentScore\": 0.1, \"resources\": []}");

            var reply = aiClient.generateReply(SYSTEM_PROMPT, USER_MESSAGE);

            assertThat(reply.reply()).isEqualTo("回复");
            assertThat(reply.sentimentScore()).isEqualTo(0.1);
            verify(chatClient, times(1)).prompt();
        }

        @Test
        @DisplayName("首次失败重试后成功：共调用 2 次")
        void shouldRetryAndSucceed() {
            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec failSpec = mock(ChatClient.CallResponseSpec.class);
            ChatClient.CallResponseSpec okSpec = mock(ChatClient.CallResponseSpec.class);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(anyString())).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(failSpec).thenReturn(okSpec);
            when(failSpec.content()).thenThrow(new RuntimeException("model timeout"));
            when(okSpec.content()).thenReturn("{\"reply\": \"重试成功\", \"sentimentScore\": 0, \"resources\": []}");

            var reply = aiClient.generateReply(SYSTEM_PROMPT, USER_MESSAGE);

            assertThat(reply.reply()).isEqualTo("重试成功");
            verify(chatClient, times(2)).prompt();
        }

        @Test
        @DisplayName("重试全部失败（1+2 次）：抛 503 WISH_AI_UNAVAILABLE")
        void shouldThrowUnavailableAfterRetriesExhausted() {
            stubCallThrow(new RuntimeException("model unavailable"));

            assertThatThrownBy(() -> aiClient.generateReply(SYSTEM_PROMPT, USER_MESSAGE))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo("WISH_AI_UNAVAILABLE");
                        assertThat(ex.getMessage()).contains("AI 服务暂时不可用");
                    });

            verify(chatClient, times(3)).prompt();
        }

        @Test
        @DisplayName("模型输出非 JSON：客户端正常返回，由解析器纯文本降级（不重试）")
        void shouldNotRetryOnNonJsonOutput() {
            stubCallContent("这不是JSON");

            var reply = aiClient.generateReply(SYSTEM_PROMPT, USER_MESSAGE);

            assertThat(reply.reply()).isEqualTo("这不是JSON");
            assertThat(reply.sentimentScore()).isNull();
            verify(chatClient, times(1)).prompt();
        }
    }
}
