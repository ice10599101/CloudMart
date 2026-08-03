package com.cloudmart.notification.service;

import com.cloudmart.notification.dto.ConversationDTO;
import com.cloudmart.notification.dto.MessageDTO;

import java.util.List;

public interface ChatService {

    List<ConversationDTO> listConversations(Long userId);

    ConversationDTO createConversation(Long userId, Long otherUserId);

    List<MessageDTO> listMessages(Long userId, Long conversationId, Long beforeId, Integer pageSize);

    MessageDTO sendMessage(Long userId, Long conversationId, String content, String type);

    MessageDTO recallMessage(Long userId, Long messageId);

    void markConversationRead(Long userId, Long conversationId);

    Long getConversationCount();

    Long getMessageCount();

    List<ConversationDTO> listAllConversations(Integer page, Integer pageSize);

    List<MessageDTO> listAllMessages(Long conversationId, Integer page, Integer pageSize);
}
