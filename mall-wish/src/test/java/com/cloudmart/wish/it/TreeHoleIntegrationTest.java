package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.config.WishAiProperties;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.GrantConsentRequest;
import com.cloudmart.wish.dto.TreeHoleMessageRequest;
import com.cloudmart.wish.enums.ConsentAction;
import com.cloudmart.wish.enums.ConsentType;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.service.TreeHoleService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.service.impl.TreeHoleReplyParser.ParsedReply;
import com.cloudmart.wish.vo.AiResourceVO;
import com.cloudmart.wish.vo.TreeHoleReplyVO;
import com.cloudmart.wish.vo.WishCreateResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 树洞 AI 链路集成测试（真实 MySQL 对话/同意记录 + 真实 Redis 限频）。
 *
 * <p>覆盖合规闭环：consent 前置（未同意 403 / 撤回后 403）→ 限频（Redis 计数超限 429）
 * → 危机词本地拦截（绝不外发 DashScope）→ PII 脱敏外发 → 对话双记录持久化
 * （USER 原文 + ASSISTANT 回复，sentiment 整数换算，session 维度）。</p>
 *
 * <p>TreeHoleAiClient 以 MockitoBean 替换（外部付费 API；危机链路需断言"未外发"），
 * 其余组件（ConsentService/AiRateLimiter/AiPrivacySanitizer/持久化）走真实链路。</p>
 */
@DisplayName("树洞 AI 链路集成测试")
class TreeHoleIntegrationTest extends WishIntegrationTestBase {

    private static final long WISHER_ID = 3001L;
    private static final long OTHER_ID = 3002L;
    private static final String RATE_LIMIT_KEY = "wish:rate:user:" + WISHER_ID + ":ai_tree_hole";

    @Autowired
    private WishService wishService;

    @Autowired
    private ConsentService consentService;

    @Autowired
    private TreeHoleService treeHoleService;

    @Autowired
    private WishAiProperties aiProperties;

    private Long treeHoleWishId;

    @BeforeEach
    void setUp() {
        Long categoryId = seedCategory("IT_TREEHOLE");
        stubUserFeign();
        WishCreateResultVO created = wishService.createWish(WISHER_ID, new CreateWishRequest(
                "树洞集成测试心愿", "验证树洞 AI 链路", null, categoryId,
                List.of("测试"), WishVisibility.TREE_HOLE, null, null, null));
        treeHoleWishId = created.id();
        grantAiConsent(WISHER_ID, ConsentAction.GRANT);
    }

    private void grantAiConsent(long userId, ConsentAction action) {
        consentService.recordConsent(userId,
                new GrantConsentRequest(ConsentType.AI_DATA_PROCESSING, "v1.0", action, null),
                "127.0.0.1", "it-test-agent");
    }

    private TreeHoleReplyVO send(String message) {
        return treeHoleService.sendTreeHoleMessage(WISHER_ID, new TreeHoleMessageRequest(treeHoleWishId, message));
    }

    private Integer conversationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wish_ai_conversation WHERE session_id = ?",
                Integer.class, "tree-hole-" + treeHoleWishId + "-" + WISHER_ID);
    }

    @Nested
    @DisplayName("合规前置")
    class ConsentGate {

        @Test
        @DisplayName("未同意 AI 数据协议：403 WISH_CONSENT_REQUIRED，不调用 AI 不落对话")
        void rejectWhenConsentMissing() {
            // 作者通过心愿校验后，无任何 consent 记录 → 命中合规前置拦截
            jdbcTemplate.update("DELETE FROM wish_consent WHERE user_id = ?", WISHER_ID);

            assertThatThrownBy(() -> send("你好"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_CONSENT_REQUIRED);

            verify(treeHoleAiClient, never()).generateReply(anyString(), anyString());
            assertThat(conversationCount()).isZero();
        }

        @Test
        @DisplayName("GRANT 后再 WITHDRAW：最新记录为准，重新返回 403")
        void rejectAfterWithdraw() {
            grantAiConsent(WISHER_ID, ConsentAction.WITHDRAW);

            assertThatThrownBy(() -> send("我撤回了授权"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_CONSENT_REQUIRED);
            assertThat(conversationCount()).isZero();
        }
    }

    @Nested
    @DisplayName("访问控制")
    class AccessControl {

        @Test
        @DisplayName("非作者发送：WISH_NOT_AUTHOR（树洞仅作者本人可对话）")
        void nonAuthorRejected() {
            assertThatThrownBy(() -> treeHoleService.sendTreeHoleMessage(
                            OTHER_ID, new TreeHoleMessageRequest(treeHoleWishId, "我想倾诉")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_AUTHOR);
        }

        @Test
        @DisplayName("非树洞心愿（PUBLIC）：WISH_VALIDATION_ERROR")
        void nonTreeHoleWishRejected() {
            Long categoryId = seedCategory("IT_PUBLIC_WISH");
            WishCreateResultVO publicWish = wishService.createWish(WISHER_ID, new CreateWishRequest(
                    "公开心愿", "非树洞", null, categoryId,
                    List.of("测试"), WishVisibility.PUBLIC, null, null, null));

            assertThatThrownBy(() -> treeHoleService.sendTreeHoleMessage(
                            WISHER_ID, new TreeHoleMessageRequest(publicWish.id(), "倾诉")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("正常链路与持久化")
    class HappyPath {

        @Test
        @DisplayName("发送成功：USER+ASSISTANT 双记录同事务落库，sentiment 0.35→35，session 维度正确")
        void persistsUserAndAssistantRecords() {
            when(treeHoleAiClient.generateReply(anyString(), anyString()))
                    .thenReturn(new ParsedReply("我收到你的心情了", 0.35,
                            List.of(new AiResourceVO("ARTICLE", "治愈文章", "https://example.com/a"))));

            TreeHoleReplyVO vo = send("今天有点累，但想继续加油");

            assertThat(vo.reply()).isEqualTo("我收到你的心情了");
            assertThat(vo.sentimentScore()).isEqualTo(0.35);

            Integer userRecords = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_ai_conversation WHERE session_id = ? AND role = 'USER' AND content = ?",
                    Integer.class, "tree-hole-" + treeHoleWishId + "-" + WISHER_ID, "今天有点累，但想继续加油");
            Integer sentiment = jdbcTemplate.queryForObject(
                    "SELECT sentiment_score FROM wish_ai_conversation WHERE session_id = ? AND role = 'ASSISTANT'",
                    Integer.class, "tree-hole-" + treeHoleWishId + "-" + WISHER_ID);
            String resources = jdbcTemplate.queryForObject(
                    "SELECT resources FROM wish_ai_conversation WHERE session_id = ? AND role = 'ASSISTANT'",
                    String.class, "tree-hole-" + treeHoleWishId + "-" + WISHER_ID);

            assertThat(userRecords).isEqualTo(1);
            assertThat(sentiment).isEqualTo(35);
            assertThat(resources).contains("ARTICLE").contains("治愈文章");
        }

        @Test
        @DisplayName("PII 脱敏：手机号不外发 DashScope，DB 保留用户原文")
        void sanitizesPiiBeforeSendingToAi() {
            when(treeHoleAiClient.generateReply(anyString(), anyString()))
                    .thenReturn(new ParsedReply("好的", 0.1, List.of()));

            send("我的手机号是13800138000，最近压力好大");

            ArgumentCaptor<String> outboundCaptor = ArgumentCaptor.forClass(String.class);
            verify(treeHoleAiClient).generateReply(anyString(), outboundCaptor.capture());
            assertThat(outboundCaptor.getValue()).contains("[已隐藏]").doesNotContain("13800138000");

            String storedRaw = jdbcTemplate.queryForObject(
                    "SELECT content FROM wish_ai_conversation WHERE session_id = ? AND role = 'USER'",
                    String.class, "tree-hole-" + treeHoleWishId + "-" + WISHER_ID);
            assertThat(storedRaw).contains("13800138000");
        }
    }

    @Nested
    @DisplayName("危机与限频")
    class CrisisAndRateLimit {

        @Test
        @DisplayName("命中危机词：本地兜底回复，绝不外发 AI，落库 sentiment=-100 且带热线资源")
        void crisisMessageInterceptedLocally() {
            TreeHoleReplyVO vo = send("我最近不想活了，太痛苦了");

            verify(treeHoleAiClient, never()).generateReply(anyString(), anyString());

            assertThat(vo.sentimentScore()).isEqualTo(-1.0);
            assertThat(vo.reply()).isEqualTo(aiProperties.getCrisisFallbackReply());
            assertThat(vo.resources()).isNotEmpty();
            assertThat(vo.resources().getFirst().type()).isEqualTo("HOTLINE");

            Integer sentiment = jdbcTemplate.queryForObject(
                    "SELECT sentiment_score FROM wish_ai_conversation WHERE session_id = ? AND role = 'ASSISTANT'",
                    Integer.class, "tree-hole-" + treeHoleWishId + "-" + WISHER_ID);
            String resources = jdbcTemplate.queryForObject(
                    "SELECT resources FROM wish_ai_conversation WHERE session_id = ? AND role = 'ASSISTANT'",
                    String.class, "tree-hole-" + treeHoleWishId + "-" + WISHER_ID);
            assertThat(sentiment).isEqualTo(-100);
            assertThat(resources).contains("HOTLINE");
        }

        @Test
        @DisplayName("超过每日 10 次限频：WISH_AI_RATE_LIMITED，不调用 AI 不落对话")
        void rateLimitExceededRejected() {
            // 预置 Redis 计数至当日上限（真实 AiRateLimiter INCR 后 = 11 > 10）
            redisTemplate.opsForValue().set(RATE_LIMIT_KEY, "10");

            assertThatThrownBy(() -> send("第 11 次倾诉"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_AI_RATE_LIMITED);

            verify(treeHoleAiClient, never()).generateReply(anyString(), anyString());
            assertThat(conversationCount()).isZero();
        }
    }
}
