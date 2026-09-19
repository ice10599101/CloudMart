package com.cloudmart.pet.service.impl;

import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 宠物聊天 AI 客户端（Spring AI ChatClient，OpenAI 兼容协议，默认智谱 GLM；
 * 与 mall-wish RemoteTreeHoleAiClient 同构）。
 *
 * <p>调用策略：失败重试 maxRetries 次（默认 2），间隔 retryIntervalMs；
 * 重试后仍失败抛 {@code PET_AI_UNAVAILABLE}——由 PetChatService 捕获后
 * 降级为模板回复（Fail-Open，聊天陪伴不因 AI 故障中断，实施文档 §1.9）。</p>
 */
@Component
@Slf4j
public class RemotePetAiClient implements PetAiClient {

    private final ChatClient chatClient;
    private final PetProperties properties;

    public RemotePetAiClient(ChatClient.Builder chatClientBuilder, PetProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public String generateReply(String systemPrompt, String userMessage) {
        PetProperties.Chat cfg = properties.getChat();
        int totalAttempts = Math.max(1, cfg.getMaxRetries() + 1);
        Exception lastException = null;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .call()
                        .content();
            } catch (Exception ex) {
                lastException = ex;
                log.warn("宠物AI调用失败, attempt={}/{}, error={}", attempt, totalAttempts, ex.getMessage());
                if (attempt < totalAttempts) {
                    sleepBeforeRetry(cfg.getRetryIntervalMs());
                }
            }
        }
        throw new BusinessException(PetErrorCodes.PET_AI_UNAVAILABLE,
                "AI 服务暂时不可用", lastException);
    }

    private void sleepBeforeRetry(long intervalMs) {
        if (intervalMs <= 0) {
            return;
        }
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BusinessException(PetErrorCodes.PET_AI_UNAVAILABLE, "AI 服务调用被中断", ie);
        }
    }
}
