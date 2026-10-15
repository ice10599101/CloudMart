package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.InteractionContextRequest;
import com.cloudmart.software.dto.software.SoftwareInteractionContextVO;
import com.cloudmart.software.dto.software.SoftwareProfileVO;
import com.cloudmart.software.dto.software.SoftwareReviewOutcomeVO;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareComment;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.feign.UserSupportFeignClient;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareCommentLikeMapper;
import com.cloudmart.software.repository.SoftwareCommentMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.common.async.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.4/6.8 评论单测（A15/A16/A17）：回复链校验、机审转人工、双向拉黑 503、
 * 隐私过滤（本人待审/占位/拉黑）、点赞幂等计数。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareCommentServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final Long OWNER_ID = 7L;
    private static final Long SOFTWARE_ID = 100L;
    private static final Long REVISION_ID = 501L;

    @Mock
    private SoftwareAccessPolicy accessPolicy;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareCommentMapper commentMapper;
    @Mock
    private SoftwareCommentLikeMapper commentLikeMapper;
    @Mock
    private CommunitySupportFeignClient communitySupportFeignClient;
    @Mock
    private UserSupportFeignClient userSupportFeignClient;
    @Mock
    private SoftwareOperationService operationService;
    @Mock
    private OutboxService outboxService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SoftwareCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        // operationService 透传业务动作
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(6)).get())
                .when(operationService).execute(anyString(), any(), anyString(), anyString(),
                        any(), any(), any(Supplier.class), any());
        service = new SoftwareCommentServiceImpl(accessPolicy, softwareMapper, commentMapper,
                commentLikeMapper, communitySupportFeignClient, userSupportFeignClient,
                operationService, outboxService, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.systemDefault()),
                transactionTemplate);

        SoftwareRevision published = new SoftwareRevision();
        published.setId(REVISION_ID);
        published.setSoftwareId(SOFTWARE_ID);
        published.setReviewStatus(SoftwareReviewStatus.APPROVED);
        when(accessPolicy.visiblePublishedRevision(SOFTWARE_ID)).thenReturn(published);
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(OWNER_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        when(userSupportFeignClient.profiles(any())).thenReturn(ApiResponse.ok(
                List.of(new SoftwareProfileVO(USER_ID, "昵称", null, true))));
        when(communitySupportFeignClient.interactionContext(any(InteractionContextRequest.class)))
                .thenReturn(ApiResponse.ok(new SoftwareInteractionContextVO(USER_ID, true,
                        null, List.of(), Instant.now())));
        when(commentMapper.insert(any(SoftwareComment.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoftwareComment.class).setId(9001L);
            return 1;
        });
    }

    private SoftwareComment comment(Long id, Long userId, Long parentId,
                                    SoftwareCommentStatus status) {
        SoftwareComment comment = new SoftwareComment();
        comment.setId(id);
        comment.setSoftwareId(SOFTWARE_ID);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setStatus(status);
        comment.setContent("评论内容" + id);
        comment.setLikeCount(0);
        comment.setPinned(false);
        comment.setLockVersion(0);
        return comment;
    }

    @Test
    @DisplayName("A15：楼中楼回复必须携带根楼 ID 且同根楼；跨根楼/跨软件拒绝；replyToUserId 服务端推导")
    void create_replyTargetValidation() {
        SoftwareComment root = comment(1L, OWNER_ID, null, SoftwareCommentStatus.VISIBLE);
        SoftwareComment otherRootReply = comment(2L, USER_ID, 8L, SoftwareCommentStatus.VISIBLE);
        when(commentMapper.selectById(1L)).thenReturn(root);
        when(commentMapper.selectById(2L)).thenReturn(otherRootReply);

        // 无 parentId 的回复 → 拒绝
        assertThatThrownBy(() -> service.create(USER_ID, SOFTWARE_ID, "k1", "内容", null, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_COMMENT_INVALID");
        // 回复目标不在同一根楼 → 拒绝
        assertThatThrownBy(() -> service.create(USER_ID, SOFTWARE_ID, "k2", "内容", 1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_COMMENT_INVALID");
    }

    @Test
    @DisplayName("A16：三级违规 CONTENT_REJECTED；二级词转人工 PENDING（仅本人可见，不加计数不通知）")
    void create_reviewOutcomes() {
        when(communitySupportFeignClient.review(any())).thenReturn(ApiResponse.ok(
                new SoftwareReviewOutcomeVO("hash", false, false, List.of(), "包含违规词汇", Instant.now())));

        assertThatThrownBy(() -> service.create(USER_ID, SOFTWARE_ID, "k3", "违规内容", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_REJECTED");
        verify(softwareMapper, never()).incrementCommentCount(any(), eq(1));

        // 二级词：needsManualReview → PENDING
        when(communitySupportFeignClient.review(any())).thenReturn(ApiResponse.ok(
                new SoftwareReviewOutcomeVO("hash", true, true, List.of("含敏感词内容"), "需人工审核", Instant.now())));
        SoftwareComment selfPending = comment(9001L, USER_ID, null, SoftwareCommentStatus.PENDING);
        when(commentMapper.selectById(9001L)).thenReturn(selfPending);

        var vo = service.create(USER_ID, SOFTWARE_ID, "k4", "含敏感词内容", null, null);

        assertThat(vo.status()).isEqualTo("SELF_PENDING");
        verify(softwareMapper, never()).incrementCommentCount(any(), eq(1));
        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("直发 VISIBLE：计数+1 + Outbox 通知软件作者（不自通知）")
    void create_visibleIncrementsAndNotifies() {
        when(communitySupportFeignClient.review(any())).thenReturn(ApiResponse.ok(
                new SoftwareReviewOutcomeVO("hash", true, false, List.of(), null, Instant.now())));
        SoftwareComment visible = comment(9001L, USER_ID, null, SoftwareCommentStatus.VISIBLE);
        when(commentMapper.selectById(9001L)).thenReturn(visible);

        var vo = service.create(USER_ID, SOFTWARE_ID, "k5", "很好的工具", null, null);

        assertThat(vo.status()).isEqualTo("VISIBLE");
        verify(softwareMapper).incrementCommentCount(SOFTWARE_ID, 1);
        ArgumentCaptor<com.cloudmart.common.async.EventEnvelope> eventCaptor =
                ArgumentCaptor.forClass(com.cloudmart.common.async.EventEnvelope.class);
        verify(outboxService).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("SOFTWARE_COMMENT_VISIBLE");
    }

    @Test
    @DisplayName("9.6：拉黑查询失败 503，绝不退回游客全量；命中拉黑的作者内容不返回")
    void pageRoots_blockedFilteringAndFailClosed() {
        SoftwareComment visible = comment(1L, 999L, null, SoftwareCommentStatus.VISIBLE);
        Page<SoftwareComment> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(visible));
        when(commentMapper.selectPage(any(), any(Wrapper.class))).thenReturn(page);
        when(commentMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        // 拉黑查询失败 → 503（不退回全量）
        when(communitySupportFeignClient.interactionContext(any(InteractionContextRequest.class)))
                .thenThrow(new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "down"));
        assertThatThrownBy(() -> service.pageRoots(SOFTWARE_ID, USER_ID, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COMMUNITY_SERVICE_UNAVAILABLE");

        // 命中拉黑 → 该楼不返回
        when(communitySupportFeignClient.interactionContext(any(InteractionContextRequest.class)))
                .thenReturn(ApiResponse.ok(new SoftwareInteractionContextVO(USER_ID, false,
                        "PEER_BLOCKED", List.of(999L), Instant.now())));
        var result = service.pageRoots(SOFTWARE_ID, USER_ID, 1, 20);
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("6.8：DELETED 根楼仅有可见回复时占位（content=null）；无回复不返回；本人待审可见")
    void pageRoots_placeholderSemantics() {
        // 有可见回复的已删根楼 → 占位
        SoftwareComment deletedRoot = comment(1L, USER_ID, null, SoftwareCommentStatus.DELETED);
        deletedRoot.setContent("被删除的评论");
        SoftwareComment visibleReply = comment(2L, OWNER_ID, 1L, SoftwareCommentStatus.VISIBLE);
        Page<SoftwareComment> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(deletedRoot));
        when(commentMapper.selectPage(any(), any(Wrapper.class))).thenReturn(page);
        when(commentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(visibleReply));

        var result = service.pageRoots(SOFTWARE_ID, 555L, 1, 20);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).status()).isEqualTo("DELETED");
        assertThat(result.getRecords().get(0).content()).isNull();
        assertThat(result.getRecords().get(0).replies()).hasSize(1);

        // 无可见回复的已删根楼 → 不返回
        when(commentMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        var empty = service.pageRoots(SOFTWARE_ID, 555L, 1, 20);
        assertThat(empty.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("A17：点赞幂等（唯一键冲突无副作用）；取消点赞计数不为负")
    void like_idempotentAndNonNegative() {
        SoftwareComment visible = comment(1L, OWNER_ID, null, SoftwareCommentStatus.VISIBLE);
        when(commentMapper.selectById(1L)).thenReturn(visible);
        when(commentLikeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.like(USER_ID, SOFTWARE_ID, 1L, true);
        verify(commentMapper).incrementLikeCount(1L);

        // 重复点赞：唯一键冲突 → 不重复计数
        when(commentLikeMapper.insert(any(com.cloudmart.software.entity.SoftwareCommentLike.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk"));
        service.like(USER_ID, SOFTWARE_ID, 1L, true);
        org.mockito.Mockito.verify(commentMapper, org.mockito.Mockito.times(1))
                .incrementLikeCount(1L);

        // 取消：删除 0 行 → 不递减
        when(commentLikeMapper.delete(any(Wrapper.class))).thenReturn(0);
        service.like(USER_ID, SOFTWARE_ID, 1L, false);
        verify(commentMapper, never()).decrementLikeCount(any());
    }

    @Test
    @DisplayName("宿主不可见：评论/列表/点赞全部 SOFTWARE_NOT_FOUND（下架同步不可访问）")
    void hostInvisible_blocksAll() {
        when(accessPolicy.visiblePublishedRevision(SOFTWARE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.create(USER_ID, SOFTWARE_ID, "k9", "内容", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_NOT_FOUND");
        assertThatThrownBy(() -> service.pageRoots(SOFTWARE_ID, USER_ID, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_NOT_FOUND");
        assertThatThrownBy(() -> service.like(USER_ID, SOFTWARE_ID, 1L, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_NOT_FOUND");
    }
}
