package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.config.WishAiProperties;
import com.cloudmart.wish.dto.AiConversationListQuery;
import com.cloudmart.wish.dto.TreeHoleMessageRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishAiConversation;
import com.cloudmart.wish.enums.AiConversationRole;
import com.cloudmart.wish.enums.AiScene;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.service.TreeHoleAiClient;
import com.cloudmart.wish.service.TreeHoleService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.service.impl.TreeHoleReplyParser.ParsedReply;
import com.cloudmart.wish.vo.AiResourceVO;
import com.cloudmart.wish.vo.TreeHoleReplyVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TreeHoleServiceImpl 单元测试。
 *
 * <p>覆盖：树洞心愿校验（404/400/403）、同意前置（403）、限频（429）、
 * 危机词本地拦截（不外发 + 热线资源）、PII 脱敏外发、对话持久化
 * （USER 原文 + ASSISTANT 情感/资源）、cursor 分页与游标校验。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TreeHoleServiceImpl 单元测试")
class TreeHoleServiceImplTest {

    @Mock
    private WishMapper wishMapper;
    @Mock
    private WishAiConversationMapper conversationMapper;
    @Mock
    private ConsentService consentService;
    @Mock
    private UserStatService userStatService;
    @Mock
    private AiRateLimiter aiRateLimiter;
    @Mock
    private TreeHoleAiClient treeHoleAiClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private TreeHoleServiceImpl treeHoleService;
    private WishAiProperties aiProperties;

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 1002L;
    private static final Long WISH_ID = 2001L;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WishAiConversation.class);
    }

    @BeforeEach
    void setUp() {
        aiProperties = new WishAiProperties();
        aiProperties.setCrisisKeywords(List.of("自杀", "轻生"));
        WishAiProperties.HotlineResource hotline = new WishAiProperties.HotlineResource();
        hotline.setType("HOTLINE");
        hotline.setTitle("全国心理援助热线");
        hotline.setUrl("tel:12356");
        aiProperties.setHotlineResources(List.of(hotline));

        treeHoleService = new TreeHoleServiceImpl(
                wishMapper, conversationMapper, consentService, userStatService,
                aiRateLimiter, new AiPrivacySanitizer(), treeHoleAiClient,
                aiProperties, transactionTemplate);

        // TransactionTemplate 直接执行回调体（单测无真实事务上下文）
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action =
                    invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // 默认放行：已同意 / 限频放行 / 默认时区
        when(consentService.hasGrantedAiDataProcessing(anyLong())).thenReturn(true);
        when(aiRateLimiter.checkTreeHoleDailyLimit(anyLong(), any())).thenReturn(true);
        when(userStatService.getUserTimezone(anyLong())).thenReturn("Asia/Shanghai");

        // 默认树洞心愿（当前用户为作者）
        when(wishMapper.selectById(WISH_ID)).thenReturn(buildTreeHoleWish());

        // 默认 AI 回复
        when(treeHoleAiClient.generateReply(anyString(), anyString())).thenReturn(
                new ParsedReply("我听到了你的心情", -0.5,
                        List.of(new AiResourceVO("ARTICLE", "治愈文章", "https://example.com/a"))));
    }

    // ========== sendTreeHoleMessage ==========

    @Nested
    @DisplayName("sendTreeHoleMessage - 前置校验")
    class PreconditionTests {

        @Test
        @DisplayName("心愿不存在 → 404 WISH_NOT_FOUND")
        void shouldRejectWhenWishNotFound() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            assertThatThrownBy(() -> treeHoleService.sendTreeHoleMessage(USER_ID, request("今天有点累")))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND));
        }

        @Test
        @DisplayName("非树洞心愿（PUBLIC）→ 400 WISH_VALIDATION_ERROR")
        void shouldRejectPublicWish() {
            Wish wish = buildTreeHoleWish();
            wish.setVisibility(WishVisibility.PUBLIC);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> treeHoleService.sendTreeHoleMessage(USER_ID, request("今天有点累")))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }

        @Test
        @DisplayName("enableAiReply=false → 400 WISH_VALIDATION_ERROR")
        void shouldRejectWhenAiReplyDisabled() {
            Wish wish = buildTreeHoleWish();
            wish.setEnableAiReply(false);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> treeHoleService.sendTreeHoleMessage(USER_ID, request("今天有点累")))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }

        @Test
        @DisplayName("非作者 → 403 WISH_NOT_AUTHOR")
        void shouldRejectNonAuthor() {
            assertThatThrownBy(() -> treeHoleService.sendTreeHoleMessage(OTHER_USER_ID, request("今天有点累")))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_AUTHOR));
        }

        @Test
        @DisplayName("未同意 AI 数据处理协议 → 403 WISH_CONSENT_REQUIRED 且不消耗限频")
        void shouldRejectWithoutConsent() {
            when(consentService.hasGrantedAiDataProcessing(USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> treeHoleService.sendTreeHoleMessage(USER_ID, request("今天有点累")))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_CONSENT_REQUIRED);
                    });

            verify(aiRateLimiter, never()).checkTreeHoleDailyLimit(anyLong(), any());
            verify(treeHoleAiClient, never()).generateReply(anyString(), anyString());
        }

        @Test
        @DisplayName("限频超限 → 429 WISH_AI_RATE_LIMITED 且不调用 AI")
        void shouldRejectWhenRateLimited() {
            when(aiRateLimiter.checkTreeHoleDailyLimit(eq(USER_ID), any())).thenReturn(false);

            assertThatThrownBy(() -> treeHoleService.sendTreeHoleMessage(USER_ID, request("今天有点累")))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_AI_RATE_LIMITED));

            verify(treeHoleAiClient, never()).generateReply(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("sendTreeHoleMessage - AI 回复链路")
    class ReplyFlowTests {

        @Test
        @DisplayName("正常路径：脱敏外发 + 持久化 USER/ASSISTANT 两条记录")
        void shouldReplyAndPersistConversation() {
            String message = "最近压力好大，我的手机 13812345678 和邮箱 a@b.com 都被骚扰";
            TreeHoleReplyVO vo = treeHoleService.sendTreeHoleMessage(USER_ID, request(message));

            assertThat(vo.reply()).isEqualTo("我听到了你的心情");
            assertThat(vo.sentimentScore()).isEqualTo(-0.5);
            assertThat(vo.resources()).hasSize(1);

            // 外发内容已脱敏
            ArgumentCaptor<String> outboundCaptor = ArgumentCaptor.forClass(String.class);
            verify(treeHoleAiClient).generateReply(anyString(), outboundCaptor.capture());
            assertThat(outboundCaptor.getValue())
                    .contains("[已隐藏]")
                    .doesNotContain("13812345678")
                    .doesNotContain("a@b.com");

            // USER 记录存储原文（含 PII），ASSISTANT 记录含情感与资源
            ArgumentCaptor<WishAiConversation> recordCaptor = ArgumentCaptor.forClass(WishAiConversation.class);
            verify(conversationMapper, org.mockito.Mockito.times(2)).insert(recordCaptor.capture());
            List<WishAiConversation> records = recordCaptor.getAllValues();

            WishAiConversation userRecord = records.get(0);
            assertThat(userRecord.getRole()).isEqualTo(AiConversationRole.USER);
            assertThat(userRecord.getContent()).isEqualTo(message);
            assertThat(userRecord.getSessionId()).isEqualTo("tree-hole-" + WISH_ID + "-" + USER_ID);
            assertThat(userRecord.getScene()).isEqualTo(AiScene.TREE_HOLE);

            WishAiConversation assistantRecord = records.get(1);
            assertThat(assistantRecord.getRole()).isEqualTo(AiConversationRole.ASSISTANT);
            assertThat(assistantRecord.getContent()).isEqualTo("我听到了你的心情");
            assertThat(assistantRecord.getSentimentScore()).isEqualTo(-50);
            assertThat(assistantRecord.getResources()).contains("治愈文章");
        }

        @Test
        @DisplayName("危机词命中：本地拦截不外发，返回兜底回复 + 热线")
        void shouldBlockCrisisLocally() {
            TreeHoleReplyVO vo = treeHoleService.sendTreeHoleMessage(USER_ID, request("我觉得活着没意思，想自杀"));

            assertThat(vo.reply()).isEqualTo(aiProperties.getCrisisFallbackReply());
            assertThat(vo.sentimentScore()).isEqualTo(-1.0);
            assertThat(vo.resources()).hasSize(1);
            assertThat(vo.resources().getFirst().type()).isEqualTo("HOTLINE");
            assertThat(vo.resources().getFirst().url()).isEqualTo("tel:12356");

            // 关键断言：危机内容绝不外发 DashScope（文档 30.4）
            verify(treeHoleAiClient, never()).generateReply(anyString(), anyString());
            // 仍持久化对话（USER 原文 + ASSISTANT 兜底，sentiment=-100）
            ArgumentCaptor<WishAiConversation> recordCaptor = ArgumentCaptor.forClass(WishAiConversation.class);
            verify(conversationMapper, org.mockito.Mockito.times(2)).insert(recordCaptor.capture());
            assertThat(recordCaptor.getAllValues().get(1).getSentimentScore()).isEqualTo(-100);
        }

        @Test
        @DisplayName("AI 客户端异常透传（503 由客户端组件抛出）")
        void shouldPropagateAiClientFailure() {
            when(treeHoleAiClient.generateReply(anyString(), anyString()))
                    .thenThrow(new BusinessException(WishErrorCodes.WISH_AI_UNAVAILABLE, "AI 服务暂时不可用"));

            assertThatThrownBy(() -> treeHoleService.sendTreeHoleMessage(USER_ID, request("今天有点累")))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_AI_UNAVAILABLE));
        }
    }

    // ========== listConversations ==========

    @Nested
    @DisplayName("listConversations - 分页查询")
    class ListTests {

        @Test
        @DisplayName("默认 TREE_HOLE 场景，id 倒序 + 情感分数换算 + resources 解析")
        void shouldListWithDefaultScene() {
            when(conversationMapper.selectList(any())).thenReturn(List.of(
                    buildConversation(3L, AiConversationRole.ASSISTANT, "回复", -50,
                            "[{\"type\":\"HOTLINE\",\"title\":\"热线\",\"url\":\"tel:12356\"}]"),
                    buildConversation(2L, AiConversationRole.USER, "倾诉", null, null)));

            TreeHoleService.ConversationPage page =
                    treeHoleService.listConversations(USER_ID, new AiConversationListQuery(null, null, 20));

            assertThat(page.records()).hasSize(2);
            assertThat(page.hasMore()).isFalse();
            assertThat(page.nextCursor()).isNull();

            var assistant = page.records().get(0);
            assertThat(assistant.role()).isEqualTo(AiConversationRole.ASSISTANT);
            assertThat(assistant.sentimentScore()).isEqualTo(-0.5);
            assertThat(assistant.resources()).hasSize(1);
            assertThat(assistant.resources().getFirst().title()).isEqualTo("热线");

            var user = page.records().get(1);
            assertThat(user.role()).isEqualTo(AiConversationRole.USER);
            assertThat(user.sentimentScore()).isNull();
            assertThat(user.resources()).isEmpty();
        }

        @Test
        @DisplayName("超出 pageSize → hasMore=true + nextCursor 为本页最后一条 id")
        void shouldReturnHasMoreAndCursor() {
            when(conversationMapper.selectList(any())).thenReturn(List.of(
                    buildConversation(3L, AiConversationRole.USER, "m3", null, null),
                    buildConversation(2L, AiConversationRole.USER, "m2", null, null),
                    buildConversation(1L, AiConversationRole.USER, "m1", null, null)));

            TreeHoleService.ConversationPage page =
                    treeHoleService.listConversations(USER_ID, new AiConversationListQuery(null, null, 2));

            assertThat(page.records()).hasSize(2);
            assertThat(page.hasMore()).isTrue();
            assertThat(page.nextCursor()).isEqualTo("2");
        }

        @Test
        @DisplayName("非法游标 → 400 WISH_VALIDATION_ERROR")
        void shouldRejectInvalidCursor() {
            assertThatThrownBy(() -> treeHoleService.listConversations(USER_ID,
                    new AiConversationListQuery(null, "not-a-number", 20)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }
    }

    // ========== fixtures ==========

    private TreeHoleMessageRequest request(String message) {
        return new TreeHoleMessageRequest(WISH_ID, message);
    }

    private Wish buildTreeHoleWish() {
        Wish wish = new Wish();
        wish.setId(WISH_ID);
        wish.setUserId(USER_ID);
        wish.setTitle("树洞");
        wish.setVisibility(WishVisibility.TREE_HOLE);
        wish.setEnableAiReply(true);
        return wish;
    }

    private WishAiConversation buildConversation(Long id, AiConversationRole role, String content,
                                                 Integer sentimentScore, String resources) {
        WishAiConversation conversation = new WishAiConversation();
        conversation.setId(id);
        conversation.setUserId(USER_ID);
        conversation.setSessionId("tree-hole-" + WISH_ID + "-" + USER_ID);
        conversation.setScene(AiScene.TREE_HOLE);
        conversation.setRole(role);
        conversation.setContent(content);
        conversation.setSentimentScore(sentimentScore);
        conversation.setResources(resources);
        conversation.setCreatedAt(LocalDateTime.now());
        return conversation;
    }
}
