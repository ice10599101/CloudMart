package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminCommentListQuery;
import com.cloudmart.wish.dto.AdminCommentStatusRequest;
import com.cloudmart.wish.dto.AdminInteractionListQuery;
import com.cloudmart.wish.entity.WishComment;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.enums.InteractionType;
import com.cloudmart.wish.enums.WishCommentStatus;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishCommentMapper;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishMapper;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理后台互动/评论服务单元测试（Sprint 1.2 BE-7）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Admin 互动/评论服务单元测试")
class AdminInteractionAndCommentServiceTest {

    @Mock
    private WishInteractionMapper wishInteractionMapper;
    @Mock
    private WishCommentMapper wishCommentMapper;
    @Mock
    private WishMapper wishMapper;
    @Mock
    private UserFeignClient userFeignClient;

    private AdminInteractionServiceImpl adminInteractionService;
    private AdminCommentServiceImpl adminCommentService;

    private static final Long USER_ID = 1001L;
    private static final Long WISH_ID = 2001L;
    private static final Long COMMENT_ID = 3001L;
    private static final Long INTERACTION_ID = 4001L;

    @BeforeEach
    void setUp() {
        // Feign 默认失败：验证昵称占位降级
        when(userFeignClient.batchGetUsers(any())).thenThrow(new RuntimeException("feign down"));

        var entity = new com.cloudmart.wish.entity.Wish();
        entity.setId(WISH_ID);
        entity.setTitle("考研上岸");
        entity.setUserId(1002L);
        when(wishMapper.selectBatchIdsIncludingDeleted(any())).thenReturn(List.of(entity));

        adminInteractionService = new AdminInteractionServiceImpl(
                wishInteractionMapper, new AdminDisplayInfoResolver(wishMapper, userFeignClient));
        adminCommentService = new AdminCommentServiceImpl(
                wishCommentMapper, new AdminDisplayInfoResolver(wishMapper, userFeignClient));
    }

    @Nested
    @DisplayName("listInteractions - 互动审计列表")
    class ListInteractionsTests {

        @Test
        @DisplayName("含已取消记录：deletedAt 保留展示、心愿标题填充、昵称降级占位")
        void list_includesDeletedRecords() {
            WishInteraction active = buildInteraction(InteractionType.LIGHT, null);
            WishInteraction cancelled = buildInteraction(InteractionType.BLESS, LocalDateTime.now());
            Page<WishInteraction> page = new Page<>(1, 20);
            page.setTotal(2);
            page.setRecords(List.of(active, cancelled));
            when(wishInteractionMapper.selectPageIncludingDeleted(any(), any())).thenReturn(page);

            var result = adminInteractionService.listInteractions(
                    new AdminInteractionListQuery(null, null, null, null, null, 1, 20));

            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getRecords().get(0).deletedAt()).isNull();
            assertThat(result.getRecords().get(1).deletedAt()).isNotNull();
            assertThat(result.getRecords().get(0).wishTitle()).isEqualTo("考研上岸");
            assertThat(result.getRecords().get(0).nickname()).isEqualTo("心愿旅人");
        }

        @Test
        @DisplayName("心愿已软删仍返回标题（审计需求）")
        void list_deletedWish_keepsTitle() {
            var deletedWish = new com.cloudmart.wish.entity.Wish();
            deletedWish.setId(WISH_ID);
            deletedWish.setTitle("已删除的心愿");
            deletedWish.setUserId(1002L);
            deletedWish.setDeletedAt(LocalDateTime.now());
            when(wishMapper.selectBatchIdsIncludingDeleted(any())).thenReturn(List.of(deletedWish));

            Page<WishInteraction> page = new Page<>(1, 20);
            page.setTotal(1);
            page.setRecords(List.of(buildInteraction(InteractionType.LIGHT, null)));
            when(wishInteractionMapper.selectPageIncludingDeleted(any(), any())).thenReturn(page);

            var result = adminInteractionService.listInteractions(
                    new AdminInteractionListQuery(WISH_ID, null, null, null, null, 1, 20));

            assertThat(result.getRecords().get(0).wishTitle()).isEqualTo("已删除的心愿");
        }
    }

    @Nested
    @DisplayName("listComments - 评论审核列表")
    class ListCommentsTests {

        @Test
        @DisplayName("敏感词命中与状态字段透出")
        void list_sensitiveHitExposed() {
            WishComment comment = buildComment();
            comment.setSensitiveHit(true);
            Page<WishComment> page = new Page<>(1, 20);
            page.setTotal(1);
            page.setRecords(List.of(comment));
            when(wishCommentMapper.selectPageIncludingDeleted(any(), any())).thenReturn(page);

            var result = adminCommentService.listComments(
                    new AdminCommentListQuery(null, null, true, WishCommentStatus.VISIBLE, 1, 20));

            assertThat(result.getRecords().get(0).sensitiveHit()).isTrue();
            assertThat(result.getRecords().get(0).status()).isEqualTo(WishCommentStatus.VISIBLE);
        }
    }

    @Nested
    @DisplayName("updateCommentStatus - 评论上下架")
    class UpdateCommentStatusTests {

        @Test
        @DisplayName("正常下架：VISIBLE → HIDDEN")
        void hide_success() {
            WishComment comment = buildComment();
            when(wishCommentMapper.selectById(COMMENT_ID)).thenReturn(comment, buildHiddenComment());

            var result = adminCommentService.updateCommentStatus(
                    COMMENT_ID, new AdminCommentStatusRequest(WishCommentStatus.HIDDEN));

            assertThat(result.status()).isEqualTo(WishCommentStatus.HIDDEN);
            ArgumentCaptor<WishComment> captor = ArgumentCaptor.forClass(WishComment.class);
            verify(wishCommentMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(WishCommentStatus.HIDDEN);
        }

        @Test
        @DisplayName("状态未变化：返回 409 冲突且不写库")
        void sameStatus_conflict() {
            when(wishCommentMapper.selectById(COMMENT_ID)).thenReturn(buildComment());

            assertThatThrownBy(() -> adminCommentService.updateCommentStatus(
                    COMMENT_ID, new AdminCommentStatusRequest(WishCommentStatus.VISIBLE)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_STATUS_CONFLICT));
            verify(wishCommentMapper, never()).updateById(any(WishComment.class));
        }

        @Test
        @DisplayName("评论不存在或已软删：返回 404")
        void notFound_404() {
            when(wishCommentMapper.selectById(COMMENT_ID)).thenReturn(null);

            assertThatThrownBy(() -> adminCommentService.updateCommentStatus(
                    COMMENT_ID, new AdminCommentStatusRequest(WishCommentStatus.HIDDEN)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND));
        }
    }

    // ================= helpers =================

    private WishInteraction buildInteraction(InteractionType type, LocalDateTime deletedAt) {
        WishInteraction interaction = new WishInteraction();
        interaction.setId(INTERACTION_ID);
        interaction.setWishId(WISH_ID);
        interaction.setUserId(USER_ID);
        interaction.setType(type);
        interaction.setStarlightCost(type == InteractionType.LIGHT ? 2 : 0);
        interaction.setDeletedAt(deletedAt);
        interaction.setCreatedAt(LocalDateTime.now());
        return interaction;
    }

    private WishComment buildComment() {
        return buildCommentWithStatus(WishCommentStatus.VISIBLE);
    }

    private WishComment buildHiddenComment() {
        return buildCommentWithStatus(WishCommentStatus.HIDDEN);
    }

    private WishComment buildCommentWithStatus(WishCommentStatus status) {
        WishComment comment = new WishComment();
        comment.setId(COMMENT_ID);
        comment.setWishId(WISH_ID);
        comment.setUserId(USER_ID);
        comment.setContent("加油");
        comment.setStatus(status);
        comment.setSensitiveHit(false);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        return comment;
    }
}
