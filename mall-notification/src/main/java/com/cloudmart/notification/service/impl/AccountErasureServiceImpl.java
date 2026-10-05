package com.cloudmart.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.notification.entity.Conversation;
import com.cloudmart.notification.entity.Message;
import com.cloudmart.notification.entity.Notification;
import com.cloudmart.notification.repository.ConversationMapper;
import com.cloudmart.notification.repository.MessageMapper;
import com.cloudmart.notification.repository.NotificationMapper;
import com.cloudmart.notification.service.AccountErasureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 账号数据擦除（T06 补齐：E10 实测 NOTIFICATION 域 ERASURE_DOMAIN_NOT_WIRED）。
 *
 * <p>通知/会话/消息是纯个人数据（无审计保留要求），物理删除；
 * 幂等——重复调用删除行数为 0 视作已擦。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureServiceImpl implements AccountErasureService {

    private final NotificationMapper notificationMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @Override
    @Transactional
    public boolean eraseUserData(Long userId) {
        int notifications = notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId));
        // 会话：本人参与（user1/user2 任一侧）的会话及其消息
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUser1Id, userId)
                        .or()
                        .eq(Conversation::getUser2Id, userId));
        int messages = 0;
        for (Conversation conversation : conversations) {
            messages += messageMapper.delete(new LambdaQueryWrapper<Message>()
                    .eq(Message::getConversationId, conversation.getId()));
        }
        int removedConversations = conversationMapper.delete(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUser1Id, userId)
                        .or()
                        .eq(Conversation::getUser2Id, userId));
        log.warn("T06 编排擦除通知数据 userId={}, notifications={}, conversations={}, messages={}",
                userId, notifications, removedConversations, messages);
        return true;
    }
}
