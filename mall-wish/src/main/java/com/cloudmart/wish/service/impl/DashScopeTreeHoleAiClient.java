package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.config.WishAiProperties;
import com.cloudmart.wish.service.TreeHoleAiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * DashScope 树洞 AI 客户端实现（qwen-turbo，文档 30.1/30.3）。
 *
 * <p>调用策略：</p>
 * <ul>
 *   <li>同步调用（API 契约返回完整 JSON：reply/sentimentScore/resources）</li>
 *   <li>失败重试 {@code maxRetries} 次（默认 2 次），间隔 {@code retryIntervalMs}（默认 1s）</li>
 *   <li>重试后仍失败 → 抛 {@code WISH_AI_UNAVAILABLE}（503），由前端展示稍后再试</li>
 *   <li>调用成功但输出非法 JSON → 由 {@link TreeHoleReplyParser} 纯文本降级，不重试</li>
 * </ul>
 */
@Component
@Slf4j
public class DashScopeTreeHoleAiClient implements TreeHoleAiClient {

    private final ChatClient chatClient;
    private final TreeHoleReplyParser replyParser;
    private final WishAiProperties aiProperties;

    public DashScopeTreeHoleAiClient(ChatClient.Builder chatClientBuilder,
                                     TreeHoleReplyParser replyParser,
                                     WishAiProperties aiProperties) {
        this.chatClient = chatClientBuilder.build();
        this.replyParser = replyParser;
        this.aiProperties = aiProperties;
    }

    @Override
    public TreeHoleReplyParser.ParsedReply generateReply(String systemPrompt, String userMessage) {
        int totalAttempts = Math.max(1, aiProperties.getMaxRetries() + 1);
        Exception lastException = null;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                String content = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .call()
                        .content();
                return replyParser.parse(content);
            } catch (Exception ex) {
                lastException = ex;
                log.warn("树洞AI调用失败, attempt={}/{}, error={}", attempt, totalAttempts, ex.getMessage());
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
