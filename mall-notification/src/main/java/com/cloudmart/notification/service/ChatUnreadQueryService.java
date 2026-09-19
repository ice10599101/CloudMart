package com.cloudmart.notification.service;

import com.cloudmart.notification.entity.Conversation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.notification.repository.ConversationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 私信未读统计（社区宠物模块提醒用，原文档 §28.3 私信提醒）。
 *
 * <p>未读计数来源：conversations 表的 user1_unread_count / user2_unread_count
 * （发起方/接收方各自的未读数），按用户身份求和；只统计未撤回消息所在会话。</p>
 */
@Service
@Slf4j
public class ChatUnreadQueryService {

    private final ConversationMapper conversationMapper;

    public ChatUnreadQueryService(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    /** 用户全部会话的未读私信总数 */
    public long countUnreadChatMessages(Long userId) {
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUser1Id, userId)
                        .or()
                        .eq(Conversation::getUser2Id, userId));
        long total = 0;
        for (Conversation conversation : conversations) {
            if (userId.equals(conversation.getUser1Id()) && conversation.getUser1UnreadCount() != null) {
                total += conversation.getUser1UnreadCount();
            } else if (userId.equals(conversation.getUser2Id()) && conversation.getUser2UnreadCount() != null) {
                total += conversation.getUser2UnreadCount();
            }
        }
        return total;
    }
}
