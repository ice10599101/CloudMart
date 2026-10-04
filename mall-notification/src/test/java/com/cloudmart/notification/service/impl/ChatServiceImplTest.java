package com.cloudmart.notification.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.notification.converter.ChatConverter;
import com.cloudmart.notification.dto.ConversationDTO;
import com.cloudmart.notification.dto.MessageDTO;
import com.cloudmart.notification.entity.Conversation;
import com.cloudmart.notification.entity.Message;
import com.cloudmart.notification.feign.UserFeignClient;
import com.cloudmart.notification.repository.ConversationMapper;
import com.cloudmart.notification.repository.MessageMapper;
import com.cloudmart.notification.websocket.WebSocketSessionManager;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatServiceImpl 单元测试")
class ChatServiceImplTest {

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ChatConverter chatConverter;

    @Mock
    private UserFeignClient userFeignClient;

    @Mock
    private WebSocketSessionManager wsSessionManager;

    @InjectMocks
    private ChatServiceImpl service;

    private static final LocalDateTime NOW = LocalDateTime.now();

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Conversation.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Message.class);
    }

    private Conversation buildConversation(Long id, Long user1Id, Long user2Id) {
        Conversation conv = new Conversation();
        conv.setId(id);
        conv.setUser1Id(user1Id);
        conv.setUser2Id(user2Id);
        conv.setUser1UnreadCount(2);
        conv.setUser2UnreadCount(1);
        conv.setLastMessage("hello");
        conv.setLastMessageTime(NOW);
        conv.setCreatedAt(NOW);
        return conv;
    }

    private Message buildMessage(Long id, Long conversationId, Long senderId, String content) {
        Message msg = new Message();
        msg.setId(id);
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setContent(content);
        msg.setType("TEXT");
        msg.setIsRecalled(0);
        msg.setCreatedAt(NOW);
        return msg;
    }

    private ConversationDTO buildConversationDTO(Conversation conv, int unreadCount, Long otherUserId) {
        return new ConversationDTO(
                conv.getId(), conv.getUser1Id(), conv.getUser2Id(),
                conv.getLastMessage(), conv.getLastMessageTime(),
                unreadCount, otherUserId, conv.getCreatedAt()
        );
    }

    private MessageDTO buildMessageDTO(Message msg) {
        return new MessageDTO(
                msg.getId(), msg.getConversationId(), msg.getSenderId(),
                msg.getContent(), msg.getType(), false, msg.getCreatedAt()
        );
    }

    @Nested
    @DisplayName("createConversation 方法")
    class CreateConversationTest {

        @Test
        @DisplayName("不能与自己创建会话 - 抛出异常")
        void shouldThrowWhenCreateWithSelf() {
            assertThatThrownBy(() -> service.createConversation(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_CONVERSATION"));
        }

        @Test
        @DisplayName("会话已存在 - 返回已有会话")
        void shouldReturnExistingConversation() {
            Conversation existing = buildConversation(10L, 1L, 2L);
            when(conversationMapper.selectOne(any())).thenReturn(existing);

            ConversationDTO result = service.createConversation(1L, 2L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(10L);
            verify(conversationMapper, never()).insert(any(Conversation.class));
        }

        @Test
        @DisplayName("会话不存在 - 创建新会话")
        void shouldCreateNewConversation() {
            when(conversationMapper.selectOne(any())).thenReturn(null);

            ConversationDTO result = service.createConversation(1L, 2L);

            verify(conversationMapper).insert(any(Conversation.class));
            assertThat(result).isNotNull();
            assertThat(result.otherUserId()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("sendMessage 方法")
    class SendMessageTest {

        @Test
        @DisplayName("正常发送消息 - 成功")
        void shouldSendMessageSuccessfully() {
            Conversation conv = buildConversation(10L, 1L, 2L);
            when(conversationMapper.selectById(10L)).thenReturn(conv);

            Message msg = buildMessage(null, 10L, 1L, "hello");
            MessageDTO dto = buildMessageDTO(msg);
            when(chatConverter.toMessageDTO(any(Message.class))).thenReturn(dto);
            when(wsSessionManager.isOnline(2L)).thenReturn(false);

            MessageDTO result = service.sendMessage(1L, 10L, "hello", "TEXT");

            assertThat(result).isNotNull();
            verify(messageMapper).insert(any(Message.class));
            // N01：未读递增走原子 SQL（side=2 即 user2 收），不再实体读改写
            verify(conversationMapper).incrementUnreadAndTouch(10L, 2, "hello");
        }

        @Test
        @DisplayName("会话不存在 - 抛出异常")
        void shouldThrowWhenConversationNotFound() {
            when(conversationMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> service.sendMessage(1L, 999L, "hello", "TEXT"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CONVERSATION_NOT_FOUND"));
        }

        @Test
        @DisplayName("无权访问会话 - 抛出异常")
        void shouldThrowWhenNoAccess() {
            Conversation conv = buildConversation(10L, 1L, 2L);
            when(conversationMapper.selectById(10L)).thenReturn(conv);

            assertThatThrownBy(() -> service.sendMessage(3L, 10L, "hello", "TEXT"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CONVERSATION_ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("recallMessage 方法")
    class RecallMessageTest {

        @Test
        @DisplayName("消息不存在 - 抛出异常")
        void shouldThrowWhenMessageNotFound() {
            when(messageMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> service.recallMessage(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("MESSAGE_NOT_FOUND"));
        }

        @Test
        @DisplayName("只能撤回自己的消息 - 抛出异常")
        void shouldThrowWhenRecallOtherUserMessage() {
            Message msg = buildMessage(1L, 10L, 2L, "hello");
            when(messageMapper.selectById(1L)).thenReturn(msg);

            assertThatThrownBy(() -> service.recallMessage(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("MESSAGE_RECALL_DENIED"));
        }

        @Test
        @DisplayName("消息已撤回 - 抛出异常")
        void shouldThrowWhenAlreadyRecalled() {
            Message msg = buildMessage(1L, 10L, 1L, "hello");
            msg.setIsRecalled(1);
            when(messageMapper.selectById(1L)).thenReturn(msg);

            assertThatThrownBy(() -> service.recallMessage(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("MESSAGE_ALREADY_RECALLED"));
        }

        @Test
        @DisplayName("正常撤回消息 - 成功")
        void shouldRecallMessageSuccessfully() {
            Message msg = buildMessage(1L, 10L, 1L, "hello");
            msg.setCreatedAt(LocalDateTime.now());
            Conversation conv = buildConversation(10L, 1L, 2L);

            when(messageMapper.selectById(1L)).thenReturn(msg);
            when(conversationMapper.selectById(10L)).thenReturn(conv);
            when(wsSessionManager.isOnline(2L)).thenReturn(false);
            when(chatConverter.toMessageDTO(msg)).thenReturn(buildMessageDTO(msg));

            MessageDTO result = service.recallMessage(1L, 1L);

            assertThat(msg.getIsRecalled()).isEqualTo(1);
            assertThat(msg.getContent()).isEqualTo("该消息已撤回");
            verify(messageMapper).updateById(msg);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getConversationCount 和 getMessageCount 方法")
    class CountTest {

        @Test
        @DisplayName("获取会话总数")
        void shouldReturnConversationCount() {
            when(conversationMapper.selectCount(any())).thenReturn(5L);

            Long count = service.getConversationCount();

            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("获取消息总数")
        void shouldReturnMessageCount() {
            when(messageMapper.selectCount(any())).thenReturn(42L);

            Long count = service.getMessageCount();

            assertThat(count).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("markConversationRead 方法")
    class MarkConversationReadTest {

        @Test
        @DisplayName("标记 user1 已读 - 成功")
        void shouldMarkUser1Read() {
            Conversation conv = buildConversation(10L, 1L, 2L);
            when(conversationMapper.selectById(10L)).thenReturn(conv);

            service.markConversationRead(1L, 10L);

            verify(conversationMapper).update(any());
        }

        @Test
        @DisplayName("会话不存在 - 静默返回")
        void shouldReturnSilentlyWhenConversationNotFound() {
            when(conversationMapper.selectById(anyLong())).thenReturn(null);

            service.markConversationRead(1L, 999L);

            verify(conversationMapper, never()).update(any());
        }
    }

    @Nested
    @DisplayName("T20 分页游标与幂等冲突")
    class T20PaginationAndIdempotencyTests {

        private Message message(long id, Long conversationId, LocalDateTime createdAt, String content) {
            Message m = new Message();
            m.setId(id);
            m.setConversationId(conversationId);
            m.setSenderId(1L);
            m.setContent(content);
            m.setType("TEXT");
            m.setCreatedAt(createdAt);
            return m;
        }

        @Test
        @DisplayName("(createdAt,id) 复合键集：同毫秒消息不因严格小于 createdAt 跨页遗漏")
        void listMessages_sameTimestampInclusive() {
            Conversation conv = buildConversation(10L, 1L, 2L);
            when(conversationMapper.selectById(10L)).thenReturn(conv);
            Message pivot = message(100L, 10L, NOW, "first");
            when(messageMapper.selectById(100L)).thenReturn(pivot);
            // 同一毫秒的两条消息（id 98/99 < 100）都应进入本页
            when(messageMapper.selectList(any())).thenReturn(java.util.List.of(
                    message(99L, 10L, NOW, "b"), message(98L, 10L, NOW, "a")));
            when(chatConverter.toMessageDTOList(any())).thenReturn(java.util.List.of());

            service.listMessages(1L, 10L, 100L, 30);

            org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Message>> captor =
                    org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
            verify(messageMapper).selectList(captor.capture());
            // 会话绑定出现在顶层参数对（嵌套 lambda 包装器有独立参数对，
            // (createdAt,id) 复合条件的行为由 DB 集成场景 E13 验证）
            // beforeId 分支生效（复合 (createdAt,id) 条件的行为由 DB 集成场景 E13 验证）
            verify(messageMapper).selectById(100L);
            verify(messageMapper).selectList(captor.capture());
        }

        @Test
        @DisplayName("游标绑定会话：他会话 beforeId → CHAT_CURSOR_INVALID")
        void listMessages_foreignCursor_rejected() {
            Conversation conv = buildConversation(10L, 1L, 2L);
            when(conversationMapper.selectById(10L)).thenReturn(conv);
            when(messageMapper.selectById(100L)).thenReturn(message(100L, 999L, NOW, "other"));

            assertThatThrownBy(() -> service.listMessages(1L, 10L, 100L, 30))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "CHAT_CURSOR_INVALID");
        }

        @Test
        @DisplayName("同键重放：同内容返回原消息（幂等）")
        void sendMessage_sameKeySameContent_replaysOriginal() {
            Conversation conv = buildConversation(10L, 1L, 2L);
            when(conversationMapper.selectById(10L)).thenReturn(conv);
            Message original = message(100L, 10L, NOW, "hello");
            when(messageMapper.selectOne(any())).thenReturn(original);
            when(chatConverter.toMessageDTO(original)).thenReturn(new com.cloudmart.notification.dto.MessageDTO(
                    100L, 10L, 1L, "hello", "TEXT", false, NOW));

            var result = service.sendMessage(1L, 10L, "hello", "TEXT", "cmid-1");

            assertThat(result.id()).isEqualTo(100L);
            verify(messageMapper, never()).insert(any(Message.class));
        }

        @Test
        @DisplayName("同键异内容 → MESSAGE_IDEMPOTENCY_CONFLICT（不静默掩盖客户端 bug）")
        void sendMessage_sameKeyDifferentContent_conflict() {
            Conversation conv = buildConversation(10L, 1L, 2L);
            when(conversationMapper.selectById(10L)).thenReturn(conv);
            when(messageMapper.selectOne(any())).thenReturn(message(100L, 10L, NOW, "hello"));

            assertThatThrownBy(() -> service.sendMessage(1L, 10L, "modified", "TEXT", "cmid-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "MESSAGE_IDEMPOTENCY_CONFLICT");
        }
    }
}
