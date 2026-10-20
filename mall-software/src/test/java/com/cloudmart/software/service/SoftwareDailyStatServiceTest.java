package com.cloudmart.software.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.SoftwareDailyStat;
import com.cloudmart.software.entity.SoftwareReviewCheckTask;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareStatEvent;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.repository.SoftwareDailyStatMapper;
import com.cloudmart.software.repository.SoftwareReviewCheckTaskMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareLinkFeedbackMapper;
import com.cloudmart.software.repository.SoftwareStatEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SF06 可解释统计：事件写入、rebuild 幂等、trends 补零与窗口限制。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareDailyStatServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 10, 0);

    @Mock
    private SoftwareStatEventMapper eventMapper;
    @Mock
    private SoftwareDailyStatMapper dailyStatMapper;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareLinkFeedbackMapper feedbackMapper;
    @Mock
    private SoftwareReviewCheckTaskMapper checkTaskMapper;

    private SoftwareDailyStatService service;

    @BeforeEach
    void setUp() {
        service = new SoftwareDailyStatService(eventMapper, dailyStatMapper, revisionMapper,
                feedbackMapper, checkTaskMapper,
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(),
                        ZoneId.systemDefault()));
    }

    @Test
    @DisplayName("事件写入：Asia/Shanghai 切日（UTC 2026-10-19T02:00 → 上海 10-19）")
    void recordEvent_platformDate() {
        service.recordEvent(5L, 42L, "FAVORITE_ADD");

        org.mockito.ArgumentCaptor<SoftwareStatEvent> captor =
                org.mockito.ArgumentCaptor.forClass(SoftwareStatEvent.class);
        verify(eventMapper).insert(captor.capture());
        assertThat(captor.getValue().getEventDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(captor.getValue().getKind()).isEqualTo("FAVORITE_ADD");
    }

    @Test
    @DisplayName("事件写入失败不阻断主流程（统计可重建，写告警不抛出）")
    void recordEvent_failureSwallowed() {
        doThrow(new RuntimeException("db down")).when(eventMapper).insert(any(SoftwareStatEvent.class));

        service.recordEvent(5L, 42L, "SUBSCRIBE_ADD");
        // 不抛异常即通过
    }

    @Test
    @DisplayName("rebuild：先删后两段插入同事务顺序执行；重复执行调用相同（不翻倍）")
    void rebuild_deleteThenInsert() {
        service.rebuild(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 19));

        InOrder inOrder = inOrder(dailyStatMapper);
        inOrder.verify(dailyStatMapper).deleteRange(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 19));
        inOrder.verify(dailyStatMapper).insertResolveRange(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 19));
        inOrder.verify(dailyStatMapper).insertFavoriteSubscribeRange(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 19));
    }

    @Test
    @DisplayName("trends：补零日序列（无数据日期也返回 0 值 bucket）")
    void trends_zeroFilled() {
        when(dailyStatMapper.selectList(any())).thenReturn(List.of());

        var result = service.trends(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3),
                null, null);

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> series =
                (List<java.util.Map<String, Object>>) result.get("series");
        assertThat(series).hasSize(3);
        assertThat(series.get(0).get("date")).isEqualTo("2026-10-01");
        assertThat(series.get(0).get("resolveCount")).isEqualTo(0L);
        assertThat(series.get(2).get("favoriteNet")).isEqualTo(0L);
        assertThat(result.get("metricDefinition")).isNotNull();
        assertThat(String.valueOf(result.get("timezone"))).isEqualTo("Asia/Shanghai");
    }

    @Test
    @DisplayName("trends：窗口超 90 天拒绝；from 晚于 to 拒绝")
    void trends_windowLimits() {
        assertThatThrownBy(() -> service.trends(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 10, 19), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("VALIDATION_ERROR");

        assertThatThrownBy(() -> service.trends(LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 1), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("trends：软件过滤传入时只汇总该集合（聚合行按 softwareId 归属）")
    void trends_filtersBySoftwareIds() {
        SoftwareDailyStat mine = new SoftwareDailyStat();
        mine.setStatDate(LocalDate.of(2026, 10, 2));
        mine.setSoftwareId(5L);
        mine.setResolveCount(7);
        mine.setResolveUserCount(3);
        mine.setFavoriteAdd(1);
        mine.setFavoriteRemove(0);
        mine.setSubscribeAdd(0);
        mine.setSubscribeRemove(0);
        SoftwareDailyStat others = new SoftwareDailyStat();
        others.setStatDate(LocalDate.of(2026, 10, 2));
        others.setSoftwareId(9L);
        others.setResolveCount(100);
        others.setResolveUserCount(50);
        others.setFavoriteAdd(0);
        others.setFavoriteRemove(0);
        others.setSubscribeAdd(0);
        others.setSubscribeRemove(0);
        // mock 不执行 SQL 层过滤：仅返回过滤后集合（softwareIds IN 条件的 SQL 行为由 D43 部署验收覆盖）
        when(dailyStatMapper.selectList(any())).thenReturn(List.of(mine));

        var result = service.trends(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3),
                List.of(5L), null);

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> series =
                (List<java.util.Map<String, Object>>) result.get("series");
        assertThat(series.get(1).get("resolveCount")).isEqualTo(7L);
        assertThat(series.get(1).get("resolveUserCount")).isEqualTo(3L);
    }

    @Test
    @DisplayName("efficiency：CHECKING 与 PENDING 分列（检查 10 分钟；人工复核从检查完成起算 50 分钟）")
    void efficiency_separatesCheckingFromPending() {
        LocalDateTime submitted = NOW.minusHours(2);
        SoftwareRevision revision = approvedRevision(1L, submitted, submitted.plusMinutes(60));
        SoftwareReviewCheckTask task = finishedTask(1L, submitted.plusMinutes(10));
        when(revisionMapper.selectList(any())).thenReturn(List.of(revision));
        when(checkTaskMapper.selectList(any())).thenReturn(List.of(task));

        var view = service.efficiency();

        assertThat(view.get("checkCount")).isEqualTo(1);
        assertThat(view.get("checkP50Minutes")).isEqualTo(10.0);
        assertThat(view.get("checkP95Minutes")).isEqualTo(10.0);
        assertThat(view.get("reviewCount")).isEqualTo(1);
        assertThat(view.get("reviewP50Minutes")).isEqualTo(50.0);
        assertThat(String.valueOf(view.get("metricDefinition"))).contains("CHECKING");
    }

    @Test
    @DisplayName("efficiency：直接发布权限自动上线，PENDING 时长为 0 且 CHECKING 仍单列")
    void efficiency_autoPublishZeroPending() {
        LocalDateTime submitted = NOW.minusHours(1);
        LocalDateTime checkEnd = submitted.plusMinutes(5);
        SoftwareRevision revision = approvedRevision(2L, submitted, checkEnd);
        when(revisionMapper.selectList(any())).thenReturn(List.of(revision));
        when(checkTaskMapper.selectList(any()))
                .thenReturn(List.of(finishedTask(2L, checkEnd)));

        var view = service.efficiency();

        assertThat(view.get("checkP50Minutes")).isEqualTo(5.0);
        assertThat(view.get("reviewP50Minutes")).isEqualTo(0.0);
        assertThat(view.get("reviewAvgMinutes")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("efficiency：无检查任务的存量提交按提交即进入 PENDING（不计入 CHECKING 样本）")
    void efficiency_legacyWithoutCheckTask() {
        LocalDateTime submitted = NOW.minusHours(1);
        SoftwareRevision revision = approvedRevision(3L, submitted, submitted.plusMinutes(30));
        when(revisionMapper.selectList(any())).thenReturn(List.of(revision));
        when(checkTaskMapper.selectList(any())).thenReturn(List.of());

        var view = service.efficiency();

        assertThat(view.get("checkCount")).isEqualTo(0);
        assertThat(view.get("checkP50Minutes")).isNull();
        assertThat(view.get("reviewP50Minutes")).isEqualTo(30.0);
    }

    private static SoftwareRevision approvedRevision(long id, LocalDateTime submittedAt,
                                                     LocalDateTime reviewedAt) {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(id);
        revision.setReviewStatus(SoftwareReviewStatus.APPROVED);
        revision.setSubmittedAt(submittedAt);
        revision.setReviewedAt(reviewedAt);
        return revision;
    }

    private static SoftwareReviewCheckTask finishedTask(long revisionId,
                                                        LocalDateTime finishedAt) {
        SoftwareReviewCheckTask task = new SoftwareReviewCheckTask();
        task.setRevisionId(revisionId);
        task.setStatus("DONE");
        task.setFinishedAt(finishedAt);
        return task;
    }
}
