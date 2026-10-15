package com.cloudmart.community.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.dto.software.InteractionContextRequest;
import com.cloudmart.community.dto.software.SoftwareReviewRequest;
import com.cloudmart.community.service.ContentReviewService;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.service.UserBlockService;
import com.cloudmart.community.vo.LevelConfigVO;
import com.cloudmart.community.vo.UserLevelVO;
import com.cloudmart.community.vo.software.SoftwareInteractionContextVO;
import com.cloudmart.community.vo.software.SoftwareLevelOptionVO;
import com.cloudmart.community.vo.software.SoftwareReviewOutcomeVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 9.6 社区只读适配契约单测：审核映射/拉黑交集/等级查询的失败关闭语义。
 */
@ExtendWith(MockitoExtension.class)
class SoftwareSupportServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock
    private GrowthService growthService;

    @Mock
    private ContentReviewService contentReviewService;

    @Mock
    private UserBlockService userBlockService;

    @Spy
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    @InjectMocks
    private SoftwareSupportServiceImpl service;

    private ContentReviewService.ReviewResult result(boolean approved, boolean manual,
                                                     String filtered, String reason) {
        return new ContentReviewService.ReviewResult(approved, manual, filtered, reason);
    }

    @Test
    @DisplayName("level：透传现有成长服务结果并记录核对时点")
    void level_mapsUserLevel() {
        when(growthService.getUserLevel(42L)).thenReturn(
                new UserLevelVO(42L, 3, 320, 1320L, "进阶作者", null, 680, "资深作者", 0.32));

        var vo = service.level(42L);

        assertThat(vo.userId()).isEqualTo(42L);
        assertThat(vo.level()).isEqualTo(3);
        assertThat(vo.levelTitle()).isEqualTo("进阶作者");
        assertThat(vo.checkedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("level：成长服务异常时 fail-closed 抛 503 语义，绝不按等级 1 放行")
    void level_failureClosed() {
        when(growthService.getUserLevel(42L)).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.level(42L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COMMUNITY_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("levelOptions：仅启用配置并裁剪为 level/title")
    void levelOptions_mapsEnabledConfigs() {
        when(growthService.getLevelConfigs()).thenReturn(List.of(
                new LevelConfigVO(1L, 1, "新手", 0, null, null, 1),
                new LevelConfigVO(2L, 2, "活跃", 100, null, null, 1)));

        List<SoftwareLevelOptionVO> options = service.levelOptions();

        assertThat(options).hasSize(2);
        assertThat(options.get(1).level()).isEqualTo(2);
        assertThat(options.get(1).title()).isEqualTo("活跃");
    }

    @Test
    @DisplayName("review：逐条过滤同序回传，二级词转人工、三级词拒绝，mediaCount 强制人工")
    void review_mapsPerTextResults() {
        when(contentReviewService.reviewContent("干净的文本"))
                .thenReturn(result(true, false, "干净的文本", null));
        when(contentReviewService.reviewContent("含二级词的文本"))
                .thenReturn(result(true, true, "含**词的文本", "内容需人工审核"));
        when(contentReviewService.reviewContent("含三级词的文本"))
                .thenReturn(result(false, false, "含三级词的文本", "内容包含违规词汇，发布被拒绝"));

        SoftwareReviewOutcomeVO outcome = service.review(new SoftwareReviewRequest(
                "req-20261015-0001", "a".repeat(64),
                List.of("干净的文本", "含二级词的文本", "含三级词的文本"), 2));

        assertThat(outcome.contentHash()).isEqualTo("a".repeat(64));
        assertThat(outcome.approved()).isFalse();
        assertThat(outcome.needsManualReview()).isTrue();
        assertThat(outcome.filteredTexts()).hasSize(3);
        assertThat(outcome.filteredTexts().get(1)).isEqualTo("含**词的文本");
        assertThat(outcome.reason()).isEqualTo("含图片/视频内容，待人工审核后展示");
        assertThat(outcome.checkedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("review：纯文本全部通过时不转人工")
    void review_cleanTextsApproved() {
        when(contentReviewService.reviewContent(anyString()))
                .thenReturn(result(true, false, null, null));

        SoftwareReviewOutcomeVO outcome = service.review(new SoftwareReviewRequest(
                "req-20261015-0002", "b".repeat(64), List.of("文本一", "文本二"), 0));

        assertThat(outcome.approved()).isTrue();
        assertThat(outcome.needsManualReview()).isFalse();
        assertThat(outcome.reason()).isNull();
        assertThat(outcome.filteredTexts()).hasSize(2);
    }

    @Test
    @DisplayName("review：请求非法（texts 超限/contentHash 格式）直接拒绝")
    void review_rejectsInvalidRequest() {
        assertThatThrownBy(() -> service.review(new SoftwareReviewRequest(
                "short", "x".repeat(64), List.of("文本"), 0)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SUPPORT_INVALID");

        assertThatThrownBy(() -> service.review(new SoftwareReviewRequest(
                "req-20261015-0003", "不是哈希", List.of("文本"), 0)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SUPPORT_INVALID");

        List<String> tooMany = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "文本" + i).toList();
        assertThatThrownBy(() -> service.review(new SoftwareReviewRequest(
                "req-20261015-0004", "c".repeat(64), tooMany, 0)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SUPPORT_INVALID");
    }

    @Test
    @DisplayName("interaction-context：blockedPeerIds=双向并集与对端交集，保持对端顺序去重")
    void interactionContext_intersectsBlockedPeers() {
        when(userBlockService.getBlockedOrBlockerIds(7L)).thenReturn(List.of(11L, 13L, 15L));

        SoftwareInteractionContextVO vo = service.interactionContext(
                new InteractionContextRequest(7L, List.of(13L, 11L, 11L, 20L)));

        assertThat(vo.canInteract()).isFalse();
        assertThat(vo.reasonCode()).isEqualTo("PEER_BLOCKED");
        assertThat(vo.blockedPeerIds()).containsExactly(13L, 11L);
        assertThat(vo.checkedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("interaction-context：无交集时放行，reasonCode 为空")
    void interactionContext_allowsCleanPeers() {
        when(userBlockService.getBlockedOrBlockerIds(7L)).thenReturn(List.of(11L));

        SoftwareInteractionContextVO vo = service.interactionContext(
                new InteractionContextRequest(7L, List.of(20L, 21L)));

        assertThat(vo.canInteract()).isTrue();
        assertThat(vo.reasonCode()).isNull();
        assertThat(vo.blockedPeerIds()).isEmpty();
    }

    @Test
    @DisplayName("interaction-context：拉黑查询异常 fail-closed，绝不按未拉黑放行")
    void interactionContext_failureClosed() {
        when(userBlockService.getBlockedOrBlockerIds(7L)).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.interactionContext(
                new InteractionContextRequest(7L, List.of(20L))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COMMUNITY_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("interaction-context：对端为空或超 100 拒绝")
    void interactionContext_rejectsInvalidPeers() {
        assertThatThrownBy(() -> service.interactionContext(
                new InteractionContextRequest(7L, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SUPPORT_INVALID");
    }
}
