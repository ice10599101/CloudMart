package com.cloudmart.wish.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateWishCommentRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishComment;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.InteractionType;
import com.cloudmart.wish.enums.WishCommentStatus;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishCommentMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WishCommentServiceImpl 单元测试。
 *
 * <p>覆盖：XSS 转义入库、敏感词先发后审标记、路径穿越拦截、
 * 二级回复扁平化、HIDDEN 不可见、cursor 分页、删除权限。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WishCommentServiceImpl 单元测试")
class WishCommentServiceImplTest {

    @Mock
    private WishCommentMapper wishCommentMapper;
    @Mock
    private WishMapper wishMapper;
    @Mock
    private UserFeignClient userFeignClient;

    private WishCommentServiceImpl commentService;

    private static final Long USER_ID = 1001L;
    private static final Long AUTHOR_ID = 1002L;
    private static final Long WISH_ID = 2001L;
    private static final Long COMMENT_ID = 3001L;

    @BeforeEach
    void setUp() {
        // 真实净化组件（含敏感词表），验证转义与敏感词标记的真实行为
        WishContentSanitizer sanitizer = new WishContentSanitizer(List.of("违禁词"));
        commentService = new WishCommentServiceImpl(wishCommentMapper, wishMapper, sanitizer, userFeignClient);

        // 默认可评论的公开心愿
        when(wishMapper.selectById(WISH_ID)).thenReturn(buildPublicWish());
        // 评论落库回填 ID
        when(wishCommentMapper.insert(any(WishComment.class))).thenAnswer(invocation -> {
            WishComment comment = invocation.getArgument(0);
            comment.setId(COMMENT_ID);
            comment.setCreatedAt(LocalDateTime.now());
            return 1;
        });
        // 用户信息 Feign 默认失败（验证占位降级）
        when(userFeignClient.batchGetUsers(anyList())).thenThrow(new RuntimeException("feign down"));
    }

    @Nested
    @DisplayName("createComment - 发表评论")
    class CreateCommentTests {

        @Test
        @DisplayName("正常评论：XSS 转义入库、默认 VISIBLE、likeCount=0")
        void create_success_escaped() {
            commentService.createComment(USER_ID, WISH_ID,
                    new CreateWishCommentRequest("<img src=x onerror=alert(1)>你好", null));

            ArgumentCaptor<WishComment> captor = ArgumentCaptor.forClass(WishComment.class);
            verify(wishCommentMapper).insert(captor.capture());
            WishComment inserted = captor.getValue();
            assertThat(inserted.getContent())
                    .doesNotContain("<img")
                    .contains("&lt;img");
            assertThat(inserted.getStatus()).isEqualTo(WishCommentStatus.VISIBLE);
            assertThat(inserted.getLikeCount()).isZero();
            assertThat(inserted.getSensitiveHit()).isFalse();
        }

        @Test
        @DisplayName("敏感词命中：仅标记不阻断（先发后审，文档 4.4）")
        void create_sensitiveWord_markedNotBlocked() {
            commentService.createComment(USER_ID, WISH_ID,
                    new CreateWishCommentRequest("这句话包含违禁词", null));

            ArgumentCaptor<WishComment> captor = ArgumentCaptor.forClass(WishComment.class);
            verify(wishCommentMapper).insert(captor.capture());
            assertThat(captor.getValue().getSensitiveHit()).isTrue();
            assertThat(captor.getValue().getStatus()).isEqualTo(WishCommentStatus.VISIBLE);
        }

        @Test
        @DisplayName("路径穿越内容：返回 400 拦截")
        void create_pathTraversal_rejected() {
            assertThatThrownBy(() -> commentService.createComment(USER_ID, WISH_ID,
                    new CreateWishCommentRequest("../../etc/passwd", null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
            verify(wishCommentMapper, never()).insert(any(WishComment.class));
        }

        @Test
        @DisplayName("回复顶级评论：parentId=父ID、replyToUserId=父作者")
        void create_replyTopLevel() {
            WishComment parent = buildComment(5001L, null, AUTHOR_ID, WishCommentStatus.VISIBLE);
            when(wishCommentMapper.selectById(5001L)).thenReturn(parent);

            commentService.createComment(USER_ID, WISH_ID,
                    new CreateWishCommentRequest("同加油！", 5001L));

            ArgumentCaptor<WishComment> captor = ArgumentCaptor.forClass(WishComment.class);
            verify(wishCommentMapper).insert(captor.capture());
            assertThat(captor.getValue().getParentId()).isEqualTo(5001L);
            assertThat(captor.getValue().getReplyToUserId()).isEqualTo(AUTHOR_ID);
        }

        @Test
        @DisplayName("回复子评论：扁平化挂载到顶级评论（parentId=祖父ID）")
        void create_replyChild_flattened() {
            WishComment topLevel = buildComment(5001L, null, AUTHOR_ID, WishCommentStatus.VISIBLE);
            WishComment child = buildComment(5002L, 5001L, 1003L, WishCommentStatus.VISIBLE);
            when(wishCommentMapper.selectById(5002L)).thenReturn(child);

            commentService.createComment(USER_ID, WISH_ID,
                    new CreateWishCommentRequest("一起加油", 5002L));

            ArgumentCaptor<WishComment> captor = ArgumentCaptor.forClass(WishComment.class);
            verify(wishCommentMapper).insert(captor.capture());
            assertThat(captor.getValue().getParentId()).isEqualTo(5001L);
            assertThat(captor.getValue().getReplyToUserId()).isEqualTo(1003L);
        }

        @Test
        @DisplayName("回复已下架评论：返回 400")
        void create_replyHidden_rejected() {
            WishComment hidden = buildComment(5001L, null, AUTHOR_ID, WishCommentStatus.HIDDEN);
            when(wishCommentMapper.selectById(5001L)).thenReturn(hidden);

            assertThatThrownBy(() -> commentService.createComment(USER_ID, WISH_ID,
                    new CreateWishCommentRequest("回复内容", 5001L)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }

        @Test
        @DisplayName("回复其他心愿的评论：返回 400")
        void create_replyOtherWish_rejected() {
            WishComment otherWishComment = buildComment(5001L, null, AUTHOR_ID, WishCommentStatus.VISIBLE);
            otherWishComment.setWishId(9999L);
            when(wishCommentMapper.selectById(5001L)).thenReturn(otherWishComment);

            assertThatThrownBy(() -> commentService.createComment(USER_ID, WISH_ID,
                    new CreateWishCommentRequest("回复内容", 5001L)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }

        @Test
        @DisplayName("PRIVATE 心愿非作者评论：返回 404")
        void create_privateWish_nonAuthor() {
            Wish wish = buildPublicWish();
            wish.setVisibility(WishVisibility.PRIVATE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> commentService.createComment(USER_ID, WISH_ID,
                    new CreateWishCommentRequest("评论", null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("listComments - 评论列表")
    class ListCommentTests {

        @Test
        @DisplayName("Feign 正常：填充昵称与被回复人昵称")
        void list_fillsUserNicknames() {
            WishComment comment = buildComment(3001L, null, AUTHOR_ID, WishCommentStatus.VISIBLE);
            comment.setReplyToUserId(1003L);
            when(wishCommentMapper.selectList(any())).thenReturn(List.of(comment));
            // doReturn 语法 re-stub：when() 语法会触发 setUp 中已注册的 thenThrow stub
            org.mockito.Mockito.doReturn(ApiResponse.ok(List.of(
                    Map.of("id", AUTHOR_ID, "nickname", "小明", "avatar", "a.png"),
                    Map.of("id", 1003L, "nickname", "小红", "avatar", "b.png")
            ))).when(userFeignClient).batchGetUsers(anyList());

            var page = commentService.listComments(WISH_ID, USER_ID, null, 20);

            assertThat(page.records()).hasSize(1);
            assertThat(page.records().get(0).nickname()).isEqualTo("小明");
            assertThat(page.records().get(0).replyToNickname()).isEqualTo("小红");
        }

        @Test
        @DisplayName("Feign 失败：降级占位昵称")
        void list_feignDown_placeholderNickname() {
            WishComment comment = buildComment(3001L, null, AUTHOR_ID, WishCommentStatus.VISIBLE);
            when(wishCommentMapper.selectList(any())).thenReturn(List.of(comment));

            var page = commentService.listComments(WISH_ID, USER_ID, null, 20);

            assertThat(page.records().get(0).nickname()).isEqualTo("心愿旅人");
        }

        @Test
        @DisplayName("返回 pageSize+1 条：截断并生成 nextCursor")
        void list_hasMore() {
            WishComment c1 = buildComment(30L, null, USER_ID, WishCommentStatus.VISIBLE);
            WishComment c2 = buildComment(20L, null, USER_ID, WishCommentStatus.VISIBLE);
            WishComment c3 = buildComment(10L, null, USER_ID, WishCommentStatus.VISIBLE);
            when(wishCommentMapper.selectList(any())).thenReturn(List.of(c1, c2, c3));

            var page = commentService.listComments(WISH_ID, USER_ID, null, 2);

            assertThat(page.records()).hasSize(2);
            assertThat(page.hasMore()).isTrue();
            assertThat(page.nextCursor()).isEqualTo("20");
        }

        @Test
        @DisplayName("非法游标：返回 400")
        void list_invalidCursor() {
            assertThatThrownBy(() -> commentService.listComments(WISH_ID, USER_ID, "abc", 20))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }
    }

    @Nested
    @DisplayName("deleteComment - 删除评论")
    class DeleteCommentTests {

        @Test
        @DisplayName("删除他人评论：返回 403")
        void delete_others_forbidden() {
            when(wishCommentMapper.selectById(COMMENT_ID))
                    .thenReturn(buildComment(COMMENT_ID, null, AUTHOR_ID, WishCommentStatus.VISIBLE));

            assertThatThrownBy(() -> commentService.deleteComment(USER_ID, WISH_ID, COMMENT_ID))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_FORBIDDEN));
        }

        @Test
        @DisplayName("删除自己的评论：软删（deleteById）")
        void delete_own_success() {
            when(wishCommentMapper.selectById(COMMENT_ID))
                    .thenReturn(buildComment(COMMENT_ID, null, USER_ID, WishCommentStatus.VISIBLE));

            commentService.deleteComment(USER_ID, WISH_ID, COMMENT_ID);

            verify(wishCommentMapper).deleteById(COMMENT_ID);
        }

        @Test
        @DisplayName("评论不存在或属其他心愿：返回 404")
        void delete_notFound() {
            when(wishCommentMapper.selectById(COMMENT_ID)).thenReturn(null);

            assertThatThrownBy(() -> commentService.deleteComment(USER_ID, WISH_ID, COMMENT_ID))
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
        return wish;
    }

    private WishComment buildComment(Long id, Long parentId, Long userId, WishCommentStatus status) {
        WishComment comment = new WishComment();
        comment.setId(id);
        comment.setWishId(WISH_ID);
        comment.setParentId(parentId);
        comment.setUserId(userId);
        comment.setContent("加油");
        comment.setStatus(status);
        comment.setLikeCount(0);
        comment.setSensitiveHit(false);
        comment.setCreatedAt(LocalDateTime.now());
        return comment;
    }
}
