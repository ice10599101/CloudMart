package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.ModerationCase;
import com.cloudmart.wish.entity.ModerationDecision;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.repository.ModerationCaseMapper;
import com.cloudmart.wish.repository.ModerationDecisionMapper;
import com.cloudmart.wish.repository.WishAppealMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishReportMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N01 治理工单核心规则测试（T27 语义）：举报配额/合并/可见性前置、
 * 决定 CAS、申诉 7 日窗口/唯一/复核人回避。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("N01 治理工单")
class ModerationServiceTest {

    private static final Long REPORTER = 5001L;
    private static final Long TARGET_WISH = 8001L;
    private static final Long AUTHOR = 7001L;
    private static final Long REVIEWER_A = 9001L;
    private static final Long REVIEWER_B = 9002L;

    @Mock
    private WishReportMapper reportMapper;
    @Mock
    private ModerationCaseMapper caseMapper;
    @Mock
    private ModerationDecisionMapper decisionMapper;
    @Mock
    private WishAppealMapper appealMapper;
    @Mock
    private WishMapper wishMapper;

    private ModerationService service;

    @BeforeAll
    static void initEntityMeta() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.cloudmart.wish.entity.Wish.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.cloudmart.wish.entity.WishAppeal.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.cloudmart.wish.entity.ModerationCase.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.cloudmart.wish.entity.WishReport.class);
    }

    @BeforeEach
    void setUp() {
        service = new ModerationService(reportMapper, caseMapper, decisionMapper,
                appealMapper, wishMapper,
                org.mockito.Mockito.mock(com.cloudmart.wish.service.impl.WishOutboxService.class));
        when(reportMapper.insert(any(com.cloudmart.wish.entity.WishReport.class))).thenReturn(1);
        when(caseMapper.insert(any(com.cloudmart.wish.entity.ModerationCase.class))).thenReturn(1);
        when(decisionMapper.insert(any(com.cloudmart.wish.entity.ModerationDecision.class))).thenAnswer(inv -> {
            com.cloudmart.wish.entity.ModerationDecision d = inv.getArgument(0);
            d.setId(99L);
            return 1;
        });
        when(appealMapper.insert(any(com.cloudmart.wish.entity.WishAppeal.class))).thenReturn(1);
    }

    private Wish publicWish() {
        Wish wish = new Wish();
        wish.setId(TARGET_WISH);
        wish.setUserId(AUTHOR);
        wish.setVisibility(WishVisibility.PUBLIC);
        wish.setAuditStatus(AuditStatus.APPROVED);
        wish.setIsVisible(true);
        return wish;
    }

    @Test
    @DisplayName("OTHER 原因缺少说明 → 422")
    void report_otherWithoutDescription_rejected() {
        assertThatThrownBy(() -> service.submitReport(REPORTER, "WISH", TARGET_WISH, "OTHER", " ", null))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
    }

    @Test
    @DisplayName("举报私密内容（不可见）→ 404 防探测")
    void report_privateWish_notFound() {
        Wish wish = publicWish();
        wish.setVisibility(WishVisibility.PRIVATE);
        when(wishMapper.selectById(TARGET_WISH)).thenReturn(wish);

        assertThatThrownBy(() -> service.submitReport(REPORTER, "WISH", TARGET_WISH, "SPAM", null, null))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND));
    }

    @Test
    @DisplayName("每日 10 次有效举报配额 → 429")
    void report_dailyQuota_rejected() {
        when(wishMapper.selectById(TARGET_WISH)).thenReturn(publicWish());
        when(reportMapper.selectCount(any())).thenReturn(10L);

        assertThatThrownBy(() -> service.submitReport(REPORTER, "WISH", TARGET_WISH, "SPAM", null, null))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(WishErrorCodes.WISH_RATE_LIMITED));
    }

    @Test
    @DisplayName("同内容同理由未结举报合并：返回原记录，不新增")
    void report_mergesActiveDuplicate() {
        when(wishMapper.selectById(TARGET_WISH)).thenReturn(publicWish());
        when(reportMapper.selectCount(any())).thenReturn(0L);
        com.cloudmart.wish.entity.WishReport existing =
                new com.cloudmart.wish.entity.WishReport();
        existing.setId(777L);
        when(reportMapper.selectOne(any())).thenReturn(existing);

        Long reportId = service.submitReport(REPORTER, "WISH", TARGET_WISH, "SPAM", null, null);

        assertThat(reportId).isEqualTo(777L);
        verify(reportMapper, never()).insert(any(com.cloudmart.wish.entity.WishReport.class));
    }

    @Test
    @DisplayName("决定：version CAS 未命中 → 409；HIDE 缺原因 → 422")
    void decide_casAndReason() {
        ModerationCase mc = new ModerationCase();
        mc.setId(1L);
        mc.setTargetType("WISH");
        mc.setTargetId(TARGET_WISH);
        mc.setStatus("OPEN");
        mc.setVersion(3);
        when(caseMapper.selectById(1L)).thenReturn(mc);
        when(wishMapper.selectById(TARGET_WISH)).thenReturn(publicWish());

        assertThatThrownBy(() -> service.decide(1L, REVIEWER_A, 2, "HIDE", "SPAM", "违规", "req-1"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(WishErrorCodes.WISH_VERSION_CONFLICT));

        assertThatThrownBy(() -> service.decide(1L, REVIEWER_A, 3, "HIDE", "SPAM", " ", "req-1"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        verify(caseMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("决定 HIDE 成功：心愿置 AUTO_HIDDEN + 不可见，case 结案，决定追加写")
    void decide_hide_applied() {
        ModerationCase mc = new ModerationCase();
        mc.setId(1L);
        mc.setTargetType("WISH");
        mc.setTargetId(TARGET_WISH);
        mc.setStatus("OPEN");
        mc.setVersion(3);
        when(caseMapper.selectById(1L)).thenReturn(mc);
        Wish target = publicWish();
        when(wishMapper.selectById(TARGET_WISH)).thenReturn(target);
        when(caseMapper.update(any(), any())).thenReturn(1);
        when(wishMapper.update(any(), any())).thenReturn(1);

        Long decisionId = service.decide(1L, REVIEWER_A, 3, "HIDE", "SPAM", "垃圾内容", "req-1");

        assertThat(decisionId).isNotNull();
        verify(wishMapper).update(any(), any());
        verify(caseMapper).update(any(), any());
        verify(decisionMapper).insert(any(ModerationDecision.class));
    }

    @Test
    @DisplayName("申诉：复核人等于原决定处理人 → 403")
    void resolveAppeal_reviewerIsOriginalActor_forbidden() {
        com.cloudmart.wish.entity.WishAppeal appeal = new com.cloudmart.wish.entity.WishAppeal();
        appeal.setId(9L);
        appeal.setDecisionId(10L);
        appeal.setStatus("PENDING");
        appeal.setVersion(0);
        when(appealMapper.selectById(9L)).thenReturn(appeal);
        ModerationDecision decision = new ModerationDecision();
        decision.setId(10L);
        decision.setCaseId(1L);
        decision.setActorId(REVIEWER_A);
        decision.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        when(decisionMapper.selectById(10L)).thenReturn(decision);

        assertThatThrownBy(() -> service.resolveAppeal(9L, REVIEWER_A, true, "复核通过"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(WishErrorCodes.WISH_FORBIDDEN));
    }

    @Test
    @DisplayName("申诉：7 日窗口外 → 422")
    void submitAppeal_afterWindow_rejected() {
        ModerationDecision decision = new ModerationDecision();
        decision.setId(10L);
        decision.setCaseId(1L);
        decision.setActorId(REVIEWER_A);
        decision.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")).minusDays(8));
        when(decisionMapper.selectById(10L)).thenReturn(decision);
        ModerationCase mc = new ModerationCase();
        mc.setId(1L);
        mc.setTargetType("WISH");
        mc.setTargetId(TARGET_WISH);
        when(caseMapper.selectById(1L)).thenReturn(mc);
        when(wishMapper.selectById(TARGET_WISH)).thenReturn(publicWish());

        assertThatThrownBy(() -> service.submitAppeal(10L, AUTHOR, "我认为误判了", null))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
    }

    @Test
    @DisplayName("申诉受理：存在其他活动 case 时不自动恢复内容")
    void resolveAppeal_accept_otherActiveCase_noRestore() {
        com.cloudmart.wish.entity.WishAppeal appeal = new com.cloudmart.wish.entity.WishAppeal();
        appeal.setId(9L);
        appeal.setDecisionId(10L);
        appeal.setStatus("PENDING");
        appeal.setVersion(0);
        when(appealMapper.selectById(9L)).thenReturn(appeal);
        ModerationDecision decision = new ModerationDecision();
        decision.setId(10L);
        decision.setCaseId(1L);
        decision.setActorId(REVIEWER_A);
        decision.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        when(decisionMapper.selectById(10L)).thenReturn(decision);
        when(appealMapper.update(any(), any())).thenReturn(1);
        ModerationCase decided = new ModerationCase();
        decided.setId(1L);
        decided.setTargetType("WISH");
        decided.setTargetId(TARGET_WISH);
        when(caseMapper.selectById(1L)).thenReturn(decided);
        when(caseMapper.selectCount(any())).thenReturn(1L); // 仍有其他活动 case

        service.resolveAppeal(9L, REVIEWER_B, true, "复核通过");

        // 恢复动作未执行（不越过其他生效下架原因）
        verify(wishMapper, never()).update(any(), any());
    }
}
