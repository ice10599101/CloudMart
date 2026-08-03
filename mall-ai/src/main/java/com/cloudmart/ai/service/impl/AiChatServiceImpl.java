package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.dto.ChatRequest;
import com.cloudmart.ai.dto.ChatResponse;
import com.cloudmart.ai.service.AiChatService;
import com.cloudmart.ai.service.VectorSearchService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 对话服务实现。
 * 对话历史存 Redis（TTL 30分钟），支持 RAG 增强回复（先向量检索再让 LLM 基于上下文回答），
 * LLM 不可用时自动降级为关键词搜索提示。
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private static final String SYSTEM_PROMPT = """
        你是 CloudMart 电商平台的智能导购助手。你的职责是：
        1. 帮助用户找到合适的商品
        2. 回答关于商品、订单、促销等电商相关问题
        3. 提供购物建议和推荐
        4. 当不确定时，建议用户使用搜索功能获取更精确的结果

        如果上下文中提供了相关商品信息，请基于这些信息进行推荐。
        保持友好、专业的语气，回答要简洁明了。
        不要编造不存在的商品或优惠信息。
        """;

    private static final String RAG_CONTEXT_TEMPLATE = """
        
        以下是根据用户问题检索到的相关商品信息，请参考这些信息回答：
        {context}
        
        """;

    private static final int MAX_CONVERSATION_HISTORY = 20;
    private static final Duration CONVERSATION_TTL = Duration.ofMinutes(30);
    private static final String REDIS_KEY_PREFIX = "ai:conversation:";

    private final ChatClient chatClient;
    private final VectorSearchService vectorSearchService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AiChatServiceImpl(ChatClient.Builder chatClientBuilder,
                             VectorSearchService vectorSearchService,
                             StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.vectorSearchService = vectorSearchService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @SentinelResource(value = "chat", fallback = "chatFallback")
    public ChatResponse chat(Long userId, ChatRequest request) {
        String conversationId = resolveConversationId(userId, request.conversationId());
        List<Message> history = loadHistory(conversationId);

        // RAG: 先做向量检索获取相关商品上下文
        String ragContext = buildRagContext(request.message());
        String effectiveMessage = request.message();
        if (!ragContext.isBlank()) {
            effectiveMessage = RAG_CONTEXT_TEMPLATE.replace("{context}", ragContext) + request.message();
        }

        history.add(new UserMessage(effectiveMessage));

        if (history.size() > MAX_CONVERSATION_HISTORY) {
            trimHistory(history);
        }

        try {
            String reply = chatClient.prompt()
                    .messages(history)
                    .call()
                    .content();

            history.add(new AssistantMessage(reply != null ? reply : ""));

            // 只存储用户原始消息到历史（不含 RAG 上下文，避免历史膨胀）
            history.set(history.size() - 2, new UserMessage(request.message()));

            saveHistory(conversationId, history);

            return new ChatResponse(reply, conversationId, false);
        } catch (Exception e) {
            log.error("AI chat failed, degrading to keyword-based response: {}", e.getMessage());
            return new ChatResponse(
                    "抱歉，智能助手暂时不可用。请尝试使用搜索功能直接查找商品。",
                    conversationId,
                    true
            );
        }
    }

    private String buildRagContext(String userMessage) {
        try {
            var results = vectorSearchService.semanticSearch(userMessage, 5);
            if (results.isEmpty()) {
                return "";
            }
            return results.stream()
                    .map(r -> "- " + r.name() + " (¥" + r.price() + "): " + r.description())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        } catch (Exception e) {
            log.warn("RAG context retrieval failed: {}", e.getMessage());
            return "";
        }
    }

    private String resolveConversationId(Long userId, String requestedId) {
        if (requestedId != null && !requestedId.isBlank()) {
            return requestedId;
        }
        return "conv:" + userId + ":" + System.currentTimeMillis();
    }

    private List<Message> loadHistory(String conversationId) {
        String key = REDIS_KEY_PREFIX + conversationId;
        String serialized = redisTemplate.opsForValue().get(key);
        if (serialized != null) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> raw = objectMapper.readValue(serialized, List.class);
                List<Message> messages = new ArrayList<>();
                for (Map<String, String> entry : raw) {
                    String role = entry.get("role");
                    String content = entry.get("content");
                    if ("system".equals(role)) {
                        messages.add(new SystemMessage(content));
                    } else if ("user".equals(role)) {
                        messages.add(new UserMessage(content));
                    } else if ("assistant".equals(role)) {
                        messages.add(new AssistantMessage(content));
                    }
                }
                return messages;
            } catch (JacksonException e) {
                log.warn("Failed to deserialize conversation history: {}", e.getMessage());
            }
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        return messages;
    }

    private void saveHistory(String conversationId, List<Message> history) {
        String key = REDIS_KEY_PREFIX + conversationId;
        try {
            List<Map<String, String>> serialized = history.stream()
                    .map(msg -> Map.of(
                            "role", getMessageRole(msg),
                            "content", msg.getText()
                    ))
                    .toList();
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(serialized), CONVERSATION_TTL);
        } catch (JacksonException e) {
            log.warn("Failed to serialize conversation history: {}", e.getMessage());
        }
    }

    private String getMessageRole(Message message) {
        if (message instanceof SystemMessage) return "system";
        if (message instanceof UserMessage) return "user";
        if (message instanceof AssistantMessage) return "assistant";
        return "user";
    }

    private void trimHistory(List<Message> history) {
        if (history.size() <= 1) {
            return;
        }
        Message systemMsg = history.getFirst();
        List<Message> trimmed = new ArrayList<>();
        trimmed.add(systemMsg);
        int start = Math.max(1, history.size() - MAX_CONVERSATION_HISTORY + 1);
        trimmed.addAll(history.subList(start, history.size()));
        history.clear();
        history.addAll(trimmed);
    }

    public ChatResponse chatFallback(Long userId, ChatRequest request, Throwable throwable) {
        log.warn("chat fallback triggered, userId={}: {}", userId, throwable.getMessage());
        return null;
    }
}
