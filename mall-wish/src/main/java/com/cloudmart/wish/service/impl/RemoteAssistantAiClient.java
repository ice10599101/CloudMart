package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.config.WishAiProperties;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.service.AssistantAiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * AI 心愿助手客户端实现（Spring AI ChatClient，OpenAI 兼容协议；Sprint 2.5）。
 *
 * <p>调用策略（对齐 RemoteTreeHoleAiClient）：</p>
 * <ul>
 *   <li>失败重试 {@code maxRetries} 次，间隔 {@code retryIntervalMs}</li>
 *   <li>重试后仍失败 → 抛 {@code WISH_AI_UNAVAILABLE}（503）</li>
 *   <li>拆解输出非法 JSON → 解析器降级（goals 空）由调用方判定，不重试</li>
 * </ul>
 */
@Component
@Slf4j
public class RemoteAssistantAiClient implements AssistantAiClient {

    private final ChatClient chatClient;
    private final GoalBreakdownParser breakdownParser;
    private final WishAiProperties aiProperties;

    public RemoteAssistantAiClient(ChatClient.Builder chatClientBuilder,
                                      GoalBreakdownParser breakdownParser,
                                      WishAiProperties aiProperties) {
        this.chatClient = chatClientBuilder.build();
        this.breakdownParser = breakdownParser;
        this.aiProperties = aiProperties;
    }

    @Override
    public GoalBreakdownParser.ParsedBreakdown generateBreakdown(String systemPrompt, String userText) {
        String content = callWithRetry(systemPrompt, userText, "goal-breakdown");
        return breakdownParser.parse(content);
    }

    @Override
    public String generateText(String systemPrompt, String userText) {
        return callWithRetry(systemPrompt, userText, "assistant-text");
    }

    /**
     * 带重试的 ChatClient 调用；重试耗尽抛 WISH_AI_UNAVAILABLE。
     */
    private String callWithRetry(String systemPrompt, String userText, String scene) {
        int totalAttempts = Math.max(1, aiProperties.getMaxRetries() + 1);
        Exception lastException = null;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return chatClient.prompt()
                        .system(systemPrompt)
                        .user(userText)
                        .call()
                        .content();
            } catch (Exception ex) {
                lastException = ex;
                log.warn("AI助手调用失败, scene={}, attempt={}/{}, error={}",
                        scene, attempt, totalAttempts, ex.getMessage());
                if (attempt < totalAttempts) {
                    sleepBeforeRetry();
                }
            }
        }
        throw new BusinessException(WishErrorCodes.WISH_AI_UNAVAILABLE,
                "AI 服务暂时不可用，请稍后再试", lastException);
    }

    private void sleepBeforeRetry() {
        long intervalMs = aiProperties.getRetryIntervalMs();
        if (intervalMs <= 0) {
            return;
        }
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BusinessException(WishErrorCodes.WISH_AI_UNAVAILABLE, "AI 服务调用被中断", ie);
        }
    }
}
