package com.cloudmart.software.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SF08 审核工作流：领取/释放协作标记与历史追溯。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareReviewWorkflowServiceTest {

    private static final Long ADMIN_A = 100L;
    private static final Long ADMIN_B = 200L;
    private static final Long REVISION_ID = 600L;
    private static final Long SOFTWARE_ID = 5L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 10, 0);

    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareReleaseMapper releaseMapper;
    @Mock
    private SoftwareSourceMapper sourceMapper;
    @Mock
    private SoftwareSourceStateMapper sourceStateMapper;
    @Mock
    private SoftwareAuditService auditService;

    private SoftwareReviewWorkflowService service;

    @BeforeAll
    static void initTableInfo() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SoftwareRevision.class);
    }

    @BeforeEach
    void setUp() {
        service = new SoftwareReviewWorkflowService(revisionMapper, releaseMapper,
                sourceMapper, sourceStateMapper, auditService,
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(),
                        ZoneId.systemDefault()));
    }

    private SoftwareRevision revision(SoftwareReviewStatus status, Long claimedBy,
                                      LocalDateTime claimedAt) {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(REVISION_ID);
        revision.setSoftwareId(SOFTWARE_ID);
        revision.setReviewStatus(status);
        revision.setClaimedBy(claimedBy);
        revision.setClaimedAt(claimedAt);
        revision.setSubmittedAt(NOW.minusHours(2));
        return revision;
    }

    @Test
    @DisplayName("领取：无主 PENDING 原子抢领成功并写审计")
    void claim_success() {
        when(revisionMapper.update(any(), any())).thenReturn(1);
        when(revisionMapper.selectById(REVISION_ID))
                .thenReturn(revision(SoftwareReviewStatus.PENDING, null, null));

        var result = service.claim(ADMIN_A, REVISION_ID);

        assertThat(result.get("claimedBy")).isEqualTo(String.valueOf(ADMIN_A));
        verify(auditService).record(SOFTWARE_ID, REVISION_ID, ADMIN_A, "ADMIN",
                "REVIEW_CLAIM", "PENDING", "PENDING", null, null);
    }

    @Test
    @DisplayName("领取：他人未超时领取被拒（SOFTWARE_REVIEW_CLAIMED）")
    void claim_rejectedWhenClaimedByOther() {
        when(revisionMapper.update(any(), any())).thenReturn(0);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(
                revision(SoftwareReviewStatus.PENDING, ADMIN_B, NOW.minusMinutes(10)));

        assertThatThrownBy(() -> service.claim(ADMIN_A, REVISION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_REVIEW_CLAIMED");
    }

    @Test
    @DisplayName("领取：超时 1 小时惰性释放，可被他人抢领")
    void claim_takeoverAfterTimeout() {
        // update 影响 0 行仅当条件不满足；claimedAt 早于超时窗口时条件满足 → 1 行
        when(revisionMapper.update(any(), any())).thenReturn(1);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(
                revision(SoftwareReviewStatus.PENDING, ADMIN_B,
                        NOW.minus(Duration.ofHours(2))));

        var result = service.claim(ADMIN_A, REVISION_ID);

        assertThat(result.get("claimedBy")).isEqualTo(String.valueOf(ADMIN_A));
    }

    @Test
    @DisplayName("释放：仅领取人本人；他人释放被拒")
    void release_onlyByClaimer() {
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(
                revision(SoftwareReviewStatus.PENDING, ADMIN_B, NOW.minusMinutes(5)));

        assertThatThrownBy(() -> service.release(ADMIN_A, REVISION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_REVIEW_CLAIMED");

        // 本人释放成功
        when(revisionMapper.update(any(), any())).thenReturn(1);
        assertThat(service.release(ADMIN_B, REVISION_ID)).isTrue();
    }

    @Test
    @DisplayName("历史：字段 diff 只包含发生变化的字段；来源状态取 source_state")
    void history_diffsOnlyChangedFields() {
        SoftwareRevision previous = revision(SoftwareReviewStatus.APPROVED, null, null);
        previous.setName("旧名称");
        previous.setSummary("未变化的简介");
        previous.setDescriptionHtml("<p>旧正文</p>");
        previous.setSubmittedAt(NOW.minusHours(6));
        previous.setId(500L);

        SoftwareRevision current = revision(SoftwareReviewStatus.PENDING, null, null);
        current.setName("新名称");
        current.setSummary("未变化的简介");
        current.setDescriptionHtml("<p>新正文</p>");
        current.setId(REVISION_ID);

        when(revisionMapper.selectPage(any(), any())).thenReturn(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<SoftwareRevision>(
                        1, 20, 2) {
                    {
                        setRecords(List.of(current, previous));
                    }
                });
        when(releaseMapper.selectList(any())).thenReturn(List.of());
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        var result = service.history(SOFTWARE_ID, 1, 20);

        assertThat(result.getRecords()).hasSize(2);
        // 最新行（current）与下一条更早提交（previous）diff：name 与 descriptionHtml 变化
        var diff = result.getRecords().get(0).get("fieldDiff");
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> fields = (List<java.util.Map<String, Object>>) diff;
        assertThat(fields).extracting(entry -> entry.get("field"))
                .containsExactlyInAnyOrder("name", "descriptionHtml");
        // 最旧行没有上一条，diff 为空
        assertThat((List<?>) result.getRecords().get(1).get("fieldDiff")).isEmpty();
    }
}
