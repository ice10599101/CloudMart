package com.cloudmart.notification.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.cloudmart.notification.entity.Conversation;

public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * N01：原子未读递增 + 会话摘要更新——替代实体读改写（并发发消息丢未读计数）。
     *
     * @param side 接收方在会话中的位置：1 = user1 收（递增 user1_unread_count），2 = user2 收
     */
    @Update("UPDATE conversations SET "
            + "last_message = #{lastMessage}, last_message_time = NOW(), "
            + "user1_unread_count = user1_unread_count + IF(#{side} = 1, 1, 0), "
            + "user2_unread_count = user2_unread_count + IF(#{side} = 2, 1, 0) "
            + "WHERE id = #{conversationId}")
    int incrementUnreadAndTouch(@Param("conversationId") Long conversationId,
                                @Param("side") int side,
                                @Param("lastMessage") String lastMessage);
}
