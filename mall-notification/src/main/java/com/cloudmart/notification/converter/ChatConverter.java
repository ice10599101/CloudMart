package com.cloudmart.notification.converter;

import com.cloudmart.notification.dto.ConversationDTO;
import com.cloudmart.notification.dto.MessageDTO;
import com.cloudmart.notification.entity.Conversation;
import com.cloudmart.notification.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatConverter {

    @Mapping(target = "unreadCount", ignore = true)
    @Mapping(target = "otherUserId", ignore = true)
    ConversationDTO toConversationDTO(Conversation entity);

    List<ConversationDTO> toConversationDTOList(List<Conversation> entities);

    @Mapping(target = "isRecalled", expression = "java(entity.getIsRecalled() != null && entity.getIsRecalled() == 1)")
    MessageDTO toMessageDTO(Message entity);

    List<MessageDTO> toMessageDTOList(List<Message> entities);
}
