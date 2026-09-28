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
import java.util.UUID;
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
        List<Message> history = loadHistory(userId, conversationId);

        // RAG: 先做向量检索获取相关商品上下文
        String ragContext = buildRagContext(request.message());
        String effectiveMessage = request.message();
        if (!ragContext.isBlank()) {
            effectiveMessage = RAG_CONTEXT_TEMPLATE.replace("{context}", ragContext) + request.message();
        }

        history.add(new UserMessage(effectiveMessage));

        try {
            String reply = chatClient.prompt()
                    .messages(history)
                    .call()
                    .content();

            history.add(new AssistantMessage(reply != null ? reply : ""));

            // 只存储用户原始消息到历史（不含 RAG 上下文，避免历史膨胀）；
            // AI-01：原子追加（RPUSH+LTRIM）替代整体覆盖——同会话并发消息不丢
            appendHistory(userId, conversationId, request.message(), reply == null ? "" : reply);

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

    /**
     * AI-01：会话 ID 由服务端签发并绑定调用者命名空间。客户端只能回传本服务
     * 之前返回的 UUID；自造 ID、旧格式 ID（无可靠 owner）或他人会话 ID 一律
     * 静默开新会话——既不返回他人历史，也不暴露会话存在性（无枚举探针）。
     */
    private String resolveConversationId(Long userId, String requestedId) {
        if (requestedId != null && !requestedId.isBlank() && isUuid(requestedId)) {
            return requestedId;
        }
        return UUID.randomUUID().toString();
    }

    /** 严格 UUID 解析，防止 ":" 等字符注入 Redis 键结构 */
    private boolean isUuid(String value) {
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** AI-01：历史键绑定 userId 命名空间——A 无法读写 B 的会话，即使猜到其会话 ID */
    private String historyKey(Long userId, String conversationId) {
        return REDIS_KEY_PREFIX + userId + ":" + conversationId;
    }

    /** AI-01：历史改为 Redis List（每条消息一个元素）；旧 JSON 整块存储的会话自然失效重建 */
    private List<Message> loadHistory(Long userId, String conversationId) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        try {
            List<String> raw = redisTemplate.opsForList().range(historyKey(userId, conversationId), 0, -1);
            if (raw != null) {
                for (String serialized : raw) {
                    try {
                        Map<String, String> entry = objectMapper.readValue(serialized, Map.class);
                        String role = entry.get("role");
                        String content = entry.get("content");
                        if ("user".equals(role)) {
                            messages.add(new UserMessage(content));
                        } else if ("assistant".equals(role)) {
                            messages.add(new AssistantMessage(content));
                        }
                    } catch (JacksonException e) {
                        log.warn("Failed to deserialize history entry, skipped: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load conversation history (degrade to fresh): {}", e.getMessage());
        }
        return messages;
    }

    /** 原子追加本轮对话（用户原始消息 + 回复）并刷新 TTL；LTRIM 保留最近 N 条 */
    private void appendHistory(Long userId, String conversationId, String userMessage, String assistantReply) {
        String key = historyKey(userId, conversationId);
        try {
            redisTemplate.opsForList().rightPushAll(key,
                    objectMapper.writeValueAsString(Map.of("role", "user", "content", userMessage)),
                    objectMapper.writeValueAsString(Map.of("role", "assistant", "content", assistantReply)));
            redisTemplate.opsForList().trim(key, -MAX_CONVERSATION_HISTORY, -1);
            redisTemplate.expire(key, CONVERSATION_TTL);
        } catch (Exception e) {
            // 历史写失败不阻断本轮回复（回复已生成），TTL 内下次对话自动重建
            log.warn("Failed to append conversation history: {}", e.getMessage());
        }
    }

    public ChatResponse chatFallback(Long userId, ChatRequest request, Throwable throwable) {
        log.warn("chat fallback triggered, userId={}: {}", userId, throwable.getMessage());
        return null;
    }
}
