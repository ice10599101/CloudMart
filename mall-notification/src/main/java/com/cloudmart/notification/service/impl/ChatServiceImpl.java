package com.cloudmart.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.notification.converter.ChatConverter;
import com.cloudmart.notification.dto.ConversationDTO;
import com.cloudmart.notification.dto.MessageDTO;
import com.cloudmart.notification.entity.Conversation;
import com.cloudmart.notification.entity.Message;
import com.cloudmart.notification.feign.UserFeignClient;
import com.cloudmart.notification.repository.ConversationMapper;
import com.cloudmart.notification.repository.MessageMapper;
import com.cloudmart.notification.service.ChatService;
import com.cloudmart.notification.websocket.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final long RECALL_WINDOW_MINUTES = 2;
    private static final long CACHE_TTL_MS = 60_000;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ChatConverter chatConverter;
    private final UserFeignClient userFeignClient;
    private final com.cloudmart.notification.feign.CommunityFeignClient communityFeignClient;
    private final WebSocketSessionManager wsSessionManager;

    private final Map<Long, UserInfo> userCache = new ConcurrentHashMap<>();
    private long cacheTimestamp = 0;

    public ChatServiceImpl(ConversationMapper conversationMapper,
                           MessageMapper messageMapper,
                           ChatConverter chatConverter,
                           UserFeignClient userFeignClient,
                           WebSocketSessionManager wsSessionManager,
                           com.cloudmart.notification.feign.CommunityFeignClient communityFeignClient) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.chatConverter = chatConverter;
        this.userFeignClient = userFeignClient;
        this.wsSessionManager = wsSessionManager;
        this.communityFeignClient = communityFeignClient;
    }

    @Override
    public List<ConversationDTO> listConversations(Long userId) {
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUser1Id, userId)
                        .or()
                        .eq(Conversation::getUser2Id, userId)
                        .orderByDesc(Conversation::getLastMessageTime)
        );

        return conversations.stream()
                .map(conv -> {
                    ConversationDTO dto = chatConverter.toConversationDTO(conv);
                    boolean isUser1 = conv.getUser1Id().equals(userId);
                    Long otherUserId = isUser1 ? conv.getUser2Id() : conv.getUser1Id();
                    Integer unreadCount = isUser1 ? conv.getUser1UnreadCount() : conv.getUser2UnreadCount();
                    return new ConversationDTO(
                            dto.id(), dto.user1Id(), dto.user2Id(),
                            dto.lastMessage(), dto.lastMessageTime(),
                            unreadCount, otherUserId, dto.createdAt()
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public ConversationDTO createConversation(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) {
            throw new BusinessException("INVALID_CONVERSATION", "不能与自己创建会话");
        }

        Long smallerId = Math.min(userId, otherUserId);
        Long largerId = Math.max(userId, otherUserId);

        Conversation existing = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUser1Id, smallerId)
                        .eq(Conversation::getUser2Id, largerId)
        );

        if (existing != null) {
            boolean isUser1 = existing.getUser1Id().equals(userId);
            Long other = isUser1 ? existing.getUser2Id() : existing.getUser1Id();
            Integer unread = isUser1 ? existing.getUser1UnreadCount() : existing.getUser2UnreadCount();
            return new ConversationDTO(
                    existing.getId(), existing.getUser1Id(), existing.getUser2Id(),
                    existing.getLastMessage(), existing.getLastMessageTime(),
                    unread, other, existing.getCreatedAt()
            );
        }

        Conversation conv = new Conversation();
        conv.setUser1Id(smallerId);
        conv.setUser2Id(largerId);
        conv.setUser1UnreadCount(0);
        conv.setUser2UnreadCount(0);
        conversationMapper.insert(conv);

        return new ConversationDTO(
                conv.getId(), conv.getUser1Id(), conv.getUser2Id(),
                null, null, 0, otherUserId, conv.getCreatedAt()
        );
    }

    @Override
    public List<MessageDTO> listMessages(Long userId, Long conversationId, Long beforeId, Integer pageSize) {
        validateConversationAccess(userId, conversationId);

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByDesc(Message::getCreatedAt);

        if (beforeId != null) {
            Message pivot = messageMapper.selectById(beforeId);
            if (pivot != null) {
                // T20：游标绑定所属会话——他会话的 beforeId 不得影响本会话分页
                if (!pivot.getConversationId().equals(conversationId)) {
                    throw new BusinessException("CHAT_CURSOR_INVALID", "分页游标不属于该会话");
                }
                // T20：(createdAt,id) 复合键集分页——同毫秒消息不再因严格小于
                // createdAt 而跨页遗漏
                wrapper.and(w -> w.lt(Message::getCreatedAt, pivot.getCreatedAt())
                        .or(x -> x.eq(Message::getCreatedAt, pivot.getCreatedAt())
                                .lt(Message::getId, beforeId)));
            }
        }

        wrapper.last("LIMIT " + pageSize);

        List<Message> messages = messageMapper.selectList(wrapper);
        // T20：防御性拷贝后再反转为时间正序（不假设 mapper 返回可变列表）
        List<Message> ordered = new java.util.ArrayList<>(messages);
        Collections.reverse(ordered);

        return chatConverter.toMessageDTOList(ordered);
    }

    @Override
    @Transactional
    public MessageDTO sendMessage(Long userId, Long conversationId, String content, String type) {
        return sendMessage(userId, conversationId, content, type, null);
    }

    /**
     * N01：发送消息（客户端幂等 + 原子未读 + 提交后推送）。
     *
     * @param clientMessageId 客户端消息幂等键（可空；同会话+发送者唯一，重发返回原消息）
     */
    @Override
    @Transactional
    public MessageDTO sendMessage(Long userId, Long conversationId, String content, String type,
                                  String clientMessageId) {
        validateConversationAccess(userId, conversationId);

        // P2-24：私信链路拉黑校验（双向；fail-open 见 CommunityFeignClient）
        Conversation blockConv = conversationMapper.selectById(conversationId);
        if (blockConv != null) {
            Long peerId = blockConv.getUser1Id().equals(userId) ? blockConv.getUser2Id() : blockConv.getUser1Id();
            try {
                var res = communityFeignClient.blockStatus(userId, peerId);
                Object blocked = res != null && res.data() != null ? res.data().get("blocked") : null;
                if (Boolean.TRUE.equals(blocked)) {
                    throw new BusinessException("CHAT_BLOCKED", "无法向该用户发送私信");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ChatServiceImpl.class)
                        .warn("P2-24 拉黑校验异常（fail-open 放行）: userId={}, peerId={}", userId, peerId, e);
            }
        }

        String msgType = (type != null && !type.isBlank()) ? type : "TEXT";

        // N01：客户端幂等——唯一键(conversation, sender, clientMessageId)冲突时重放原消息
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            Message existing = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                    .eq(Message::getConversationId, conversationId)
                    .eq(Message::getSenderId, userId)
                    .eq(Message::getClientMessageId, clientMessageId)
                    .last("LIMIT 1"));
            if (existing != null) {
                // T20：同键重放校验内容一致性——同键异内容/异类型为幂等冲突（409），
                // 不静默返回原消息掩盖客户端 bug
                if (!existing.getContent().equals(content)
                        || !existing.getType().equals(msgType)) {
                    throw new BusinessException("MESSAGE_IDEMPOTENCY_CONFLICT",
                            "消息幂等键已绑定不同内容");
                }
                return chatConverter.toMessageDTO(existing);
            }
        }

        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(userId);
        message.setContent(content);
        message.setType(msgType);
        message.setClientMessageId(clientMessageId);
        message.setIsRecalled(0);
        try {
            messageMapper.insert(message);
        } catch (org.springframework.dao.DuplicateKeyException race) {
            Message winner = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                    .eq(Message::getConversationId, conversationId)
                    .eq(Message::getSenderId, userId)
                    .eq(Message::getClientMessageId, clientMessageId)
                    .last("LIMIT 1"));
            if (winner != null) {
                return chatConverter.toMessageDTO(winner);
            }
            throw new BusinessException("CHAT_MESSAGE_CONFLICT", "消息发送冲突，请重试");
        }

        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            // N01：原子未读递增（实体读改写在并发下丢计数）+ 摘要更新同一 SQL
            int side = conv.getUser1Id().equals(userId) ? 2 : 1;
            conversationMapper.incrementUnreadAndTouch(conversationId, side,
                    content.length() > 200 ? content.substring(0, 200) : content);
            Long recipientId = side == 2 ? conv.getUser2Id() : conv.getUser1Id();

            // N01：事务提交后再推 WebSocket（事务内推送可能在回滚后发出幽灵消息）
            final Message pushed = message;
            final Long recv = recipientId;
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager
                        .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                pushChatMessage(recv, pushed, conversationId);
                            }
                        });
            } else {
                pushChatMessage(recv, pushed, conversationId);
            }
        }

        return chatConverter.toMessageDTO(message);
    }

    @Override
    @Transactional
    public MessageDTO recallMessage(Long userId, Long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("MESSAGE_NOT_FOUND", "消息不存在");
        }
        if (!message.getSenderId().equals(userId)) {
            throw new BusinessException("MESSAGE_RECALL_DENIED", "只能撤回自己的消息");
        }
        if (message.getIsRecalled() == 1) {
            throw new BusinessException("MESSAGE_ALREADY_RECALLED", "消息已撤回");
        }
        if (message.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(RECALL_WINDOW_MINUTES))) {
            throw new BusinessException("MESSAGE_RECALL_EXPIRED", "消息超过2分钟，无法撤回");
        }

        message.setIsRecalled(1);
        message.setContent("该消息已撤回");
        messageMapper.updateById(message);

        Conversation conv = conversationMapper.selectById(message.getConversationId());
        if (conv != null) {
            Long recipientId = conv.getUser1Id().equals(userId) ? conv.getUser2Id() : conv.getUser1Id();
            pushRecallNotification(recipientId, messageId, message.getConversationId());
        }

        return chatConverter.toMessageDTO(message);
    }

    @Override
    public Long getConversationCount() {
        return conversationMapper.selectCount(new LambdaQueryWrapper<>());
    }

    @Override
    public Long getMessageCount() {
        return messageMapper.selectCount(new LambdaQueryWrapper<>());
    }

    @Override
    public List<ConversationDTO> listAllConversations(Integer page, Integer pageSize) {
        Page<Conversation> pageParam = new Page<>(page, pageSize);
        Page<Conversation> result = conversationMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Conversation>()
                        .orderByDesc(Conversation::getLastMessageTime)
        );
        return result.getRecords().stream()
                .map(conv -> {
                    ConversationDTO dto = chatConverter.toConversationDTO(conv);
                    return new ConversationDTO(
                            dto.id(), dto.user1Id(), dto.user2Id(),
                            dto.lastMessage(), dto.lastMessageTime(),
                            conv.getUser1UnreadCount() + conv.getUser2UnreadCount(),
                            conv.getUser1Id(), dto.createdAt()
                    );
                })
                .toList();
    }

    @Override
    public List<MessageDTO> listAllMessages(Long conversationId, Integer page, Integer pageSize) {
        Page<Message> pageParam = new Page<>(page, pageSize);
        Page<Message> result = messageMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByDesc(Message::getCreatedAt)
        );
        return chatConverter.toMessageDTOList(result.getRecords());
    }

    @Override
    @Transactional
    public void markConversationRead(Long userId, Long conversationId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            return;
        }

        if (conv.getUser1Id().equals(userId)) {
            conversationMapper.update(
                    new LambdaUpdateWrapper<Conversation>()
                            .eq(Conversation::getId, conversationId)
                            .set(Conversation::getUser1UnreadCount, 0)
            );
        } else if (conv.getUser2Id().equals(userId)) {
            conversationMapper.update(
                    new LambdaUpdateWrapper<Conversation>()
                            .eq(Conversation::getId, conversationId)
                            .set(Conversation::getUser2UnreadCount, 0)
            );
        }
    }

    private void pushChatMessage(Long recipientId, Message message, Long conversationId) {
        if (!wsSessionManager.isOnline(recipientId)) {
            return;
        }
        try {
            String json = """
                    {"type":"CHAT_MESSAGE","conversationId":%d,"messageId":%d,"senderId":%d,"content":"%s","msgType":"%s","createdAt":"%s"}
                    """.formatted(
                    conversationId, message.getId(), message.getSenderId(),
                    escapeJson(message.getContent()), message.getType(), message.getCreatedAt()
            );
            wsSessionManager.sendRawMessage(recipientId, json);
        } catch (Exception e) {
            log.warn("Failed to push chat message to userId={}: {}", recipientId, e.getMessage());
        }
    }

    private void pushRecallNotification(Long recipientId, Long messageId, Long conversationId) {
        if (!wsSessionManager.isOnline(recipientId)) {
            return;
        }
        try {
            String json = """
                    {"type":"CHAT_RECALL","conversationId":%d,"messageId":%d}
                    """.formatted(conversationId, messageId);
            wsSessionManager.sendRawMessage(recipientId, json);
        } catch (Exception e) {
            log.warn("Failed to push recall notification to userId={}: {}", recipientId, e.getMessage());
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void validateConversationAccess(Long userId, Long conversationId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new BusinessException("CONVERSATION_NOT_FOUND", "会话不存在");
        }
        if (!conv.getUser1Id().equals(userId) && !conv.getUser2Id().equals(userId)) {
            throw new BusinessException("CONVERSATION_ACCESS_DENIED", "无权访问此会话");
        }
    }

    public Map<Long, UserInfo> batchGetUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        if (System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            Map<Long, UserInfo> cached = new HashMap<>();
            for (Long id : userIds) {
                UserInfo info = userCache.get(id);
                if (info != null) {
                    cached.put(id, info);
                }
            }
            if (cached.size() == userIds.size()) {
                return cached;
            }
        }

        Map<Long, UserInfo> result = new HashMap<>();
        Set<Long> missing = new HashSet<>(userIds);
        missing.removeAll(userCache.keySet());

        if (!missing.isEmpty()) {
            try {
                ApiResponse<List<Map<String, Object>>> response = userFeignClient.batchGetUsers(new ArrayList<>(missing));
                if (response != null && response.data() != null) {
                    for (Map<String, Object> userMap : response.data()) {
                        Long id = ((Number) userMap.get("id")).longValue();
                        String nickname = (String) userMap.getOrDefault("nickname", "");
                        String avatar = (String) userMap.getOrDefault("avatar", "");
                        UserInfo info = new UserInfo(id, nickname, avatar);
                        userCache.put(id, info);
                        result.put(id, info);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to batch fetch users from mall-user: {}", e.getMessage());
            }
        }

        for (Long id : userIds) {
            UserInfo cached = userCache.get(id);
            if (cached != null) {
                result.putIfAbsent(id, cached);
            }
        }

        cacheTimestamp = System.currentTimeMillis();
        return result;
    }

    public record UserInfo(Long id, String nickname, String avatar) {}
}
