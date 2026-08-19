package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateInteractionRequest;
import com.cloudmart.wish.dto.InteractionListQuery;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.InteractionType;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.mq.WishStatEventProducer;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.InteractionService;
import com.cloudmart.wish.service.UserStatService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InteractionServiceImpl 单元测试。
 *
 * <p>覆盖：互动类型校验、可见性 404 语义、限频 429、同求三道防线、
 * 星光同事务结算（扣减/发放/日上限截断）、取消不退星光、cursor 分页。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InteractionServiceImpl 单元测试")
class InteractionServiceImplTest {

    @Mock
    private WishMapper wishMapper;
    @Mock
    private WishInteractionMapper wishInteractionMapper;
    @Mock
    private UserStatService userStatService;
    @Mock
    private InteractionRateLimiter rateLimiter;
    @Mock
    private WishStatEventProducer statEventProducer;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private InteractionServiceImpl interactionService;

    private static final Long USER_ID = 1001L;
    private static final Long AUTHOR_ID = 1002L;
    private static final Long WISH_ID = 2001L;
    private static final Long INTERACTION_ID = 3001L;

    @BeforeEach
    void setUp() {
        // 真实净化组件（含敏感词表），验证 XSS 转义与敏感词标记的真实行为
        WishContentSanitizer sanitizer = new WishContentSanitizer(List.of("违禁词"));
        interactionService = new InteractionServiceImpl(
                wishMapper, wishInteractionMapper, userStatService,
                rateLimiter, sanitizer, statEventProducer, userFeignClient, transactionTemplate
        );

        // TransactionTemplate 直接执行回调体（单测无真实事务上下文）
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        // 默认放行的限频与默认时区
        when(rateLimiter.checkUserDailyLimit(anyLong(), any(), any())).thenReturn(true);
        when(rateLimiter.checkWishLightLimit(anyLong())).thenReturn(true);
        when(rateLimiter.checkBlessPerWish(anyLong(), anyLong(), any())).thenReturn(true);
        when(rateLimiter.tryAcquireSameWishUnique(anyLong(), anyLong())).thenReturn(true);
        when(userStatService.getUserTimezone(anyLong())).thenReturn("Asia/Shanghai");

        // 默认可互动的公开心愿（他人发布）
        when(wishMapper.selectById(WISH_ID)).thenReturn(buildPublicWish());
        // 作者今日被点亮/被同求计数（未达上限）
        when(wishInteractionMapper.countIncludingDeletedSince(anyLong(), any(), any()))
                .thenReturn(0L);
        // 互动落库回填 ID
        when(wishInteractionMapper.insert(any(WishInteraction.class))).thenAnswer(invocation -> {
            WishInteraction interaction = invocation.getArgument(0);
            interaction.setId(INTERACTION_ID);
            interaction.setCreatedAt(LocalDateTime.now());
            return 1;
        });
        // 同求 DB 存在性校验默认无记录
        when(wishInteractionMapper.selectCount(any())).thenReturn(0L);
    }

    // ========== createInteraction ==========

    @Nested
    @DisplayName("createInteraction - 前置校验")
    class CreatePreconditionTests {

        @Test
        @DisplayName("ANON_STAR 未开放：返回 400 WISH_INTERACTION_TYPE_INVALID")
        void anonStar_notEnabled() {
            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.ANON_STAR, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_INTERACTION_TYPE_INVALID));
        }

        @Test
        @DisplayName("心愿不存在：返回 404 WISH_NOT_FOUND")
        void wishNotFound() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.LIGHT, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND));
        }

        @Test
        @DisplayName("PRIVATE 心愿非作者：返回 404（不暴露存在性）")
        void privateWish_nonAuthor_404() {
            Wish wish = buildPublicWish();
            wish.setVisibility(WishVisibility.PRIVATE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.LIGHT, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND));
        }

        @Test
        @DisplayName("PRIVATE 心愿作者本人可互动")
        void privateWish_author_allowed() {
            Wish wish = buildPublicWish();
            wish.setUserId(USER_ID);
            wish.setVisibility(WishVisibility.PRIVATE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            var result = interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.BLESS, "加油"));

            assertThat(result.type()).isEqualTo(InteractionType.BLESS);
        }
    }

    @Nested
    @DisplayName("createInteraction - 限频（429）")
    class CreateRateLimitTests {

        @Test
        @DisplayName("用户日限频达上限：返回 429 且不落库")
        void userDailyLimit_exceeded() {
            when(rateLimiter.checkUserDailyLimit(eq(USER_ID), eq(InteractionType.LIGHT), any()))
                    .thenReturn(false);

            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.LIGHT, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_RATE_LIMITED));
            verify(wishInteractionMapper, never()).insert(any(WishInteraction.class));
        }

        @Test
        @DisplayName("心愿被点亮达日上限：返回 429")
        void wishLightLimit_exceeded() {
            when(rateLimiter.checkWishLightLimit(WISH_ID)).thenReturn(false);

            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.LIGHT, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_RATE_LIMITED));
        }

        @Test
        @DisplayName("同一心愿当日重复祝福：返回 429")
        void blessPerWish_exceeded() {
            when(rateLimiter.checkBlessPerWish(eq(USER_ID), eq(WISH_ID), any())).thenReturn(false);

            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.BLESS, "加油")))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_RATE_LIMITED));
        }
    }

    @Nested
    @DisplayName("createInteraction - 祝福内容净化")
    class CreateBlessContentTests {

        @Test
        @DisplayName("祝福内容为空：返回 400")
        void blessContent_blank() {
            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.BLESS, " ")))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }

        @Test
        @DisplayName("祝福内容含路径穿越：返回 400")
        void blessContent_pathTraversal() {
            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.BLESS, "../etc/passwd")))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }

        @Test
        @DisplayName("祝福内容 XSS 转义后入库")
        void blessContent_escaped() {
            interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.BLESS, "<script>alert(1)</script>"));

            ArgumentCaptor<WishInteraction> captor = ArgumentCaptor.forClass(WishInteraction.class);
            verify(wishInteractionMapper).insert(captor.capture());
            assertThat(captor.getValue().getContent())
                    .doesNotContain("<script>")
                    .contains("&lt;script&gt;");
        }
    }

    @Nested
    @DisplayName("createInteraction - 同求唯一三道防线")
    class CreateSameWishTests {

        @Test
        @DisplayName("Redis 占位失败（第一道防线）：返回 409")
        void sameWish_redisOccupied() {
            when(rateLimiter.tryAcquireSameWishUnique(USER_ID, WISH_ID)).thenReturn(false);

            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.SAME_WISH, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_ALREADY_INTERACTED));
        }

        @Test
        @DisplayName("DB 已存在同求（第二道防线）：返回 409 并释放 Redis 占位")
        void sameWish_dbExists_releasesPlaceholder() {
            when(wishInteractionMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.SAME_WISH, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_ALREADY_INTERACTED));
            verify(rateLimiter).releaseSameWishUnique(USER_ID, WISH_ID);
        }

        @Test
        @DisplayName("同求成功：作者获得 2 星光、点亮者无扣减")
        void sameWish_success() {
            var result = interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.SAME_WISH, null));

            assertThat(result.type()).isEqualTo(InteractionType.SAME_WISH);
            assertThat(result.starlightCost()).isZero();
            verify(userStatService, never()).spendStarlight(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
            verify(userStatService).earnStarlight(eq(AUTHOR_ID), eq(2),
                    eq(ResourceLogSource.SAME_WISHED), eq(INTERACTION_ID));
        }
    }

    @Nested
    @DisplayName("createInteraction - 点亮星光结算")
    class CreateLightTests {

        @Test
        @DisplayName("点亮成功：扣点亮者 2 星光、发作者 1 星光、发送 helped 事件")
        void light_success() {
            Wish latest = buildPublicWish();
            latest.setLightCount(11);
            when(wishMapper.selectById(WISH_ID)).thenReturn(buildPublicWish(), latest);

            var result = interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.LIGHT, null));

            assertThat(result.starlightCost()).isEqualTo(2);
            assertThat(result.lightCount()).isEqualTo(11);
            verify(userStatService).spendStarlight(USER_ID, 2, ResourceLogSource.LIGHT_OTHER, INTERACTION_ID);
            verify(userStatService).earnStarlight(AUTHOR_ID, 1, ResourceLogSource.LIGHTED, INTERACTION_ID);
            verify(statEventProducer).publishHelpedEvent(USER_ID);
        }

        @Test
        @DisplayName("作者被点亮达日上限（第 21 次）：不再发放但互动仍成功")
        void light_authorDailyCapReached() {
            when(wishInteractionMapper.countIncludingDeletedSince(
                    eq(WISH_ID), eq(InteractionType.LIGHT.name()), any()))
                    .thenReturn(21L); // 大于 DAILY_EARN_LIGHT_CAP=20（本次为第 21+1 次）

            var result = interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.LIGHT, null));

            assertThat(result.type()).isEqualTo(InteractionType.LIGHT);
            verify(userStatService).spendStarlight(USER_ID, 2, ResourceLogSource.LIGHT_OTHER, INTERACTION_ID);
            verify(userStatService, never()).earnStarlight(eq(AUTHOR_ID), org.mockito.ArgumentMatchers.anyInt(), any(), any());
        }

        @Test
        @DisplayName("点亮者星光不足：402 异常传播（事务回滚）")
        void light_insufficientStarlight() {
            doThrow(new BusinessException(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT, "星光余额不足"))
                    .when(userStatService).spendStarlight(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any(), any());

            assertThatThrownBy(() -> interactionService.createInteraction(
                    USER_ID, WISH_ID, new CreateInteractionRequest(InteractionType.LIGHT, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT));
        }
    }

    // ========== revokeInteraction ==========

    @Nested
    @DisplayName("revokeInteraction - 取消互动")
    class RevokeTests {

        @Test
        @DisplayName("取消他人互动：返回 403")
        void revoke_othersInteraction_forbidden() {
            when(wishInteractionMapper.selectById(INTERACTION_ID))
                    .thenReturn(buildInteraction(InteractionType.LIGHT, USER_ID));

            assertThatThrownBy(() -> interactionService.revokeInteraction(
                    9999L, WISH_ID, INTERACTION_ID))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_FORBIDDEN));
        }

        @Test
        @DisplayName("互动记录不存在：返回 404")
        void revoke_notFound() {
            when(wishInteractionMapper.selectById(INTERACTION_ID)).thenReturn(null);

            assertThatThrownBy(() -> interactionService.revokeInteraction(
                    USER_ID, WISH_ID, INTERACTION_ID))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_INTERACTION_NOT_FOUND));
        }

        @Test
        @DisplayName("取消点亮：计数-1、不退星光、不发 MQ")
        void revoke_light_noRefund() {
            when(wishInteractionMapper.selectById(INTERACTION_ID))
                    .thenReturn(buildInteraction(InteractionType.LIGHT, USER_ID));

            var result = interactionService.revokeInteraction(USER_ID, WISH_ID, INTERACTION_ID);

            assertThat(result.revoked()).isTrue();
            verify(wishInteractionMapper).deleteById(INTERACTION_ID);
            verify(userStatService, never()).earnStarlight(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
            verify(userStatService, never()).spendStarlight(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
            verify(statEventProducer, never()).publishHelpedEvent(anyLong());
        }

        @Test
        @DisplayName("取消同求：释放唯一占位（允许重新同求）")
        void revoke_sameWish_releasesPlaceholder() {
            when(wishInteractionMapper.selectById(INTERACTION_ID))
                    .thenReturn(buildInteraction(InteractionType.SAME_WISH, USER_ID));

            interactionService.revokeInteraction(USER_ID, WISH_ID, INTERACTION_ID);

            verify(rateLimiter).releaseSameWishUnique(USER_ID, WISH_ID);
        }
    }

    // ========== listInteractions ==========

    @Nested
    @DisplayName("listInteractions - cursor 分页")
    class ListTests {

        @Test
        @DisplayName("返回 pageSize+1 条：截断至 pageSize 并生成 nextCursor")
        void list_hasMore() {
            List<WishInteraction> records = List.of(
                    buildInteraction(InteractionType.LIGHT, USER_ID),
                    buildInteraction(InteractionType.LIGHT, USER_ID),
                    buildInteraction(InteractionType.LIGHT, USER_ID));
            records.get(0).setId(30L);
            records.get(1).setId(20L);
            records.get(2).setId(10L);
            when(wishInteractionMapper.selectList(any())).thenReturn(records);

            var page = interactionService.listInteractions(
                    WISH_ID, USER_ID, new InteractionListQuery(null, null, 2));

            assertThat(page.records()).hasSize(2);
            assertThat(page.hasMore()).isTrue();
            assertThat(page.nextCursor()).isEqualTo("20");
        }

        @Test
        @DisplayName("不足一页：无 nextCursor")
        void list_noMore() {
            when(wishInteractionMapper.selectList(any())).thenReturn(List.of());

            var page = interactionService.listInteractions(
                    WISH_ID, USER_ID, new InteractionListQuery(null, null, 2));

            assertThat(page.records()).isEmpty();
            assertThat(page.hasMore()).isFalse();
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        @DisplayName("非法游标：返回 400")
        void list_invalidCursor() {
            assertThatThrownBy(() -> interactionService.listInteractions(
                    WISH_ID, USER_ID, new InteractionListQuery(null, "abc", 2)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }
    }

    @Nested
    @DisplayName("listMyInteractions - 我的互动状态")
    class MyInteractionTests {

        @Test
        @DisplayName("今日 BLESS 记录：createdToday=true")
        void my_todayBless() {
            WishInteraction bless = buildInteraction(InteractionType.BLESS, USER_ID);
            bless.setContent("加油");
            when(wishInteractionMapper.selectList(any())).thenReturn(List.of(bless));

            var result = interactionService.listMyInteractions(USER_ID, WISH_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).type()).isEqualTo(InteractionType.BLESS);
            assertThat(result.get(0).createdToday()).isTrue();
            assertThat(result.get(0).content()).isEqualTo("加油");
        }

        @Test
        @DisplayName("昨日 BLESS 记录：createdToday=false（今日可再次祝福）")
        void my_yesterdayBless() {
            WishInteraction bless = buildInteraction(InteractionType.BLESS, USER_ID);
            bless.setCreatedAt(LocalDateTime.now().minusDays(1));
            when(wishInteractionMapper.selectList(any())).thenReturn(List.of(bless));

            var result = interactionService.listMyInteractions(USER_ID, WISH_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).createdToday()).isFalse();
        }

        @Test
        @DisplayName("多类型记录：全部返回且含 id 供取消")
        void my_mixedTypes() {
            WishInteraction light = buildInteraction(InteractionType.LIGHT, USER_ID);
            light.setId(11L);
            WishInteraction sameWish = buildInteraction(InteractionType.SAME_WISH, USER_ID);
            sameWish.setId(12L);
            when(wishInteractionMapper.selectList(any())).thenReturn(List.of(sameWish, light));

            var result = interactionService.listMyInteractions(USER_ID, WISH_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting("id").containsExactly(12L, 11L);
        }

        @Test
        @DisplayName("无互动：返回空列表")
        void my_empty() {
            when(wishInteractionMapper.selectList(any())).thenReturn(List.of());

            var result = interactionService.listMyInteractions(USER_ID, WISH_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("心愿不可见：返回 404 语义")
        void my_wishNotFound() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            assertThatThrownBy(() -> interactionService.listMyInteractions(USER_ID, WISH_ID))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND));
        }
    }

    // ================= helpers =================

    private Wish buildPublicWish() {
        Wish wish = new Wish();
        wish.setId(WISH_ID);
        wish.setUserId(AUTHOR_ID);
        wish.setTitle("考研上岸");
        wish.setVisibility(WishVisibility.PUBLIC);
        wish.setStatus(WishStatus.ACTIVE);
        wish.setAuditStatus(AuditStatus.APPROVED);
        wish.setIsVisible(true);
        wish.setLightCount(10);
        wish.setSameWishCount(5);
        wish.setBlessCount(3);
        return wish;
    }

    private WishInteraction buildInteraction(InteractionType type, Long userId) {
        WishInteraction interaction = new WishInteraction();
        interaction.setId(INTERACTION_ID);
        interaction.setWishId(WISH_ID);
        interaction.setUserId(userId);
        interaction.setType(type);
        interaction.setStarlightCost(type == InteractionType.LIGHT ? 2 : 0);
        interaction.setCreatedAt(LocalDateTime.now());
        return interaction;
    }
}
