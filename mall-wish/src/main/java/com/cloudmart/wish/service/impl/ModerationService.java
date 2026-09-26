package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.ModerationCase;
import com.cloudmart.wish.entity.ModerationDecision;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishAppeal;
import com.cloudmart.wish.entity.WishReport;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.repository.ModerationCaseMapper;
import com.cloudmart.wish.repository.ModerationDecisionMapper;
import com.cloudmart.wish.repository.WishAppealMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * 统一治理工单服务（N01，任务书 §7）。
 *
 * <p>关键规则：</p>
 * <ul>
 *   <li>举报：仅可举报当前有权看到的内容；OTHER 必填说明；每天最多 10 次有效新举报；
 *       同一内容同一理由的未结举报合并（dedup_key 唯一，结案置空释放）；</li>
 *   <li>工单：一份内容同一 revision 仅一个活动 case（生成列唯一键兜底）；
 *       OPEN→IN_REVIEW→RESOLVED（允许 OPEN→RESOLVED）；决定追加写；</li>
 *   <li>决定：HIDE/RESTORE 落到内容状态（WISH：is_visible / audit_status）；
 *       CAS version 防双审核员互相覆盖；复核人不得为原决定处理人；</li>
 *   <li>申诉：作者对最新治理决定 7 日内提出；同决定同作者一条；通过恢复前检查
 *       其他生效下架原因（无其他活动 case 才恢复）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    static final Set<String> TARGET_TYPES = Set.of("WISH", "WISH_COMMENT", "GROWTH_RECORD",
            "FULFILLMENT", "DRIFT_BOTTLE", "BOTTLE_COMMENT");
    static final Set<String> REASON_CODES = Set.of("SPAM", "ABUSE", "FRAUD", "PRIVACY", "OTHER");
    private static final int DAILY_REPORT_QUOTA = 10;
    private static final int MAX_EVIDENCE = 3;
    private static final java.time.ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Shanghai");

    private final WishReportMapper reportMapper;
    private final ModerationCaseMapper caseMapper;
    private final ModerationDecisionMapper decisionMapper;
    private final WishAppealMapper appealMapper;
    private final WishMapper wishMapper;
    private final WishOutboxService outboxService;

    // ---------------- 举报（用户） ----------------

    @Transactional(rollbackFor = Exception.class)
    public Long submitReport(Long reporterId, String targetType, Long targetId,
                             String reasonCode, String description, List<String> evidenceRefs) {
        String type = normalize(targetType, TARGET_TYPES, "目标类型");
        String reason = normalize(reasonCode, REASON_CODES, "举报原因");
        if ("OTHER".equals(reason) && (description == null || description.isBlank())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "OTHER 原因必须填写说明");
        }
        if (description != null && description.length() > 500) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "说明不能超过500字");
        }
        if (evidenceRefs != null && evidenceRefs.size() > MAX_EVIDENCE) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "证据附件最多3个");
        }
        // 只能举报当前有权看到的内容（WISH 强校验；其他类型由各自域负责）
        if ("WISH".equals(type)) {
            Wish wish = wishMapper.selectById(targetId);
            if (wish == null || !accessPolicy().isPublicReadable(wish)) {
                throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "内容不存在");
            }
        }
        // 每日有效新举报配额（平台日界）
        LocalDateTime dayStart = java.time.LocalDate.now(PLATFORM_ZONE).atStartOfDay();
        Long todayCount = reportMapper.selectCount(new LambdaQueryWrapper<WishReport>()
                .eq(WishReport::getReporterId, reporterId)
                .ge(WishReport::getCreatedAt, dayStart));
        if (todayCount != null && todayCount >= DAILY_REPORT_QUOTA) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED, "今日举报次数已达上限");
        }

        // 未结举报同内容同理由合并
        String dedupKey = dedupKey(type, targetId, reason);
        WishReport existing = reportMapper.selectOne(new LambdaQueryWrapper<WishReport>()
                .eq(WishReport::getDedupKey, dedupKey).last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }

        WishReport report = new WishReport();
        report.setReporterId(reporterId);
        report.setTargetType(type);
        report.setTargetId(targetId);
        report.setReasonCode(reason);
        report.setDescription(description);
        report.setEvidenceRefs(evidenceRefs == null ? null
                : com.cloudmart.wish.util.WishJsonUtils.stringifyList(evidenceRefs));
        report.setStatus("PENDING");
        report.setDedupKey(dedupKey);

        // 关联或创建活动 case
        ModerationCase activeCase = caseMapper.selectOne(new LambdaQueryWrapper<ModerationCase>()
                .eq(ModerationCase::getTargetType, type)
                .eq(ModerationCase::getTargetId, targetId)
                .in(ModerationCase::getStatus, "OPEN", "IN_REVIEW")
                .last("LIMIT 1"));
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException ex) {
            // 并发同键举报：按合并语义返回已存在记录
            WishReport winner = reportMapper.selectOne(new LambdaQueryWrapper<WishReport>()
                    .eq(WishReport::getDedupKey, dedupKey).last("LIMIT 1"));
            return winner != null ? winner.getId() : report.getId();
        }
        Long caseId = activeCase != null ? activeCase.getId()
                : createCase(type, targetId, reporterId);
        report.setCaseId(caseId);
        reportMapper.updateById(report);
        return report.getId();
    }

    private Long createCase(String type, Long targetId, Long reporterId) {
        ModerationCase mc = new ModerationCase();
        mc.setTargetType(type);
        mc.setTargetId(targetId);
        mc.setTargetRevision(0L);
        mc.setStatus("OPEN");
        mc.setVersion(0);
        try {
            caseMapper.insert(mc);
        } catch (DuplicateKeyException ex) {
            // 并发建 case：唯一键保证仅一个活动 case
            ModerationCase active = caseMapper.selectOne(new LambdaQueryWrapper<ModerationCase>()
                    .eq(ModerationCase::getTargetType, type)
                    .eq(ModerationCase::getTargetId, targetId)
                    .in(ModerationCase::getStatus, "OPEN", "IN_REVIEW")
                    .last("LIMIT 1"));
            if (active != null) {
                return active.getId();
            }
            throw new BusinessException(WishErrorCodes.WISH_OPERATION_IN_PROGRESS, "工单创建冲突，请重试");
        }
        return mc.getId();
    }

    public List<WishReport> listMyReports(Long reporterId, Long cursor, int pageSize) {
        return reportMapper.selectList(new LambdaQueryWrapper<WishReport>()
                .eq(WishReport::getReporterId, reporterId)
                .lt(cursor != null, WishReport::getId, cursor)
                .orderByDesc(WishReport::getId)
                .last("LIMIT " + Math.min(Math.max(pageSize, 1), 50)));
    }

    // ---------------- 决定（治理端） ----------------

    @Transactional(rollbackFor = Exception.class)
    public Long decide(Long caseId, Long actorId, Integer version, String decision,
                       String reasonCode, String reasonText, String requestId) {
        ModerationCase mc = caseMapper.selectById(caseId);
        if (mc == null || "RESOLVED".equals(mc.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "工单不存在或已结案");
        }
        String dec = normalize(decision, Set.of("NO_ACTION", "HIDE", "RESTORE"), "决定");
        if (!"NO_ACTION".equals(dec) && (reasonText == null || reasonText.isBlank())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "HIDE/RESTORE 必须填写原因");
        }
        if (version == null || mc.getVersion() == null || !version.equals(mc.getVersion())) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "工单已被并发处理，请刷新");
        }

        String beforeState = snapshotTarget(mc.getTargetType(), mc.getTargetId());
        if ("WISH".equals(mc.getTargetType())) {
            applyWishDecision(mc.getTargetId(), dec);
        }

        // 结案 CAS
        int affected = caseMapper.update(null, new LambdaUpdateWrapper<ModerationCase>()
                .eq(ModerationCase::getId, caseId)
                .eq(ModerationCase::getVersion, version)
                .set(ModerationCase::getStatus, "RESOLVED")
                .set(ModerationCase::getAssigneeId, actorId)
                .setSql("version = version + 1")
                .set(ModerationCase::getResolvedAt, LocalDateTime.now(ZoneId.of("UTC"))));
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "工单已被并发处理，请刷新");
        }

        ModerationDecision record = new ModerationDecision();
        record.setCaseId(caseId);
        record.setActorId(actorId);
        record.setDecision(dec);
        record.setReasonCode(reasonCode);
        record.setReasonText(reasonText);
        record.setBeforeState(beforeState);
        record.setAfterState(snapshotTarget(mc.getTargetType(), mc.getTargetId()));
        record.setTargetRevision(mc.getTargetRevision());
        record.setRequestId(requestId);
        decisionMapper.insert(record);

        // 关联举报结案并释放 dedup 唯一
        reportMapper.update(null, new LambdaUpdateWrapper<WishReport>()
                .eq(WishReport::getCaseId, caseId)
                .set(WishReport::getStatus, "RESOLVED")
                .set(WishReport::getDedupKey, null));
        return record.getId();
    }

    /** HIDE/RESTORE 落到心愿状态（其他目标类型由各自域治理接入后生效）。 */
    private void applyWishDecision(Long wishId, String decision) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "目标心愿不存在");
        }
        LambdaUpdateWrapper<Wish> uw = new LambdaUpdateWrapper<Wish>().eq(Wish::getId, wishId);
        if ("HIDE".equals(decision)) {
            uw.set(Wish::getIsVisible, false);
            uw.set(Wish::getAuditStatus, AuditStatus.AUTO_HIDDEN);
        } else if ("RESTORE".equals(decision)) {
            uw.set(Wish::getIsVisible, true);
            uw.set(Wish::getAuditStatus, AuditStatus.APPROVED);
            uw.set(Wish::getRejectReason, null);
        }
        wishMapper.update(null, uw);
        outboxService.publish("WISH", wishId, 0L, "WishVisibilityChanged",
                java.util.Map.of("wishId", wishId, "decision", decision));
    }

    private String snapshotTarget(String targetType, Long targetId) {
        if ("WISH".equals(targetType)) {
            Wish wish = wishMapper.selectById(targetId);
            if (wish == null) {
                return "DELETED";
            }
            return wish.getAuditStatus() + "/" + (Boolean.TRUE.equals(wish.getIsVisible()) ? "VISIBLE" : "HIDDEN");
        }
        return "N/A";
    }

    // ---------------- 申诉（用户 + 复核） ----------------

    @Transactional(rollbackFor = Exception.class)
    public Long submitAppeal(Long decisionId, Long appellantId, String statement, List<String> evidenceRefs) {
        if (statement == null || statement.length() < 1 || statement.length() > 1000) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "申诉陈述须为1-1000字");
        }
        if (evidenceRefs != null && evidenceRefs.size() > MAX_EVIDENCE) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "证据附件最多3个");
        }
        ModerationDecision decision = decisionMapper.selectById(decisionId);
        if (decision == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "治理决定不存在");
        }
        // 仅被处理作者可申诉（WISH：心愿作者）
        if ("WISH".equals(targetTypeOf(decision.getCaseId()))) {
            Long caseIdValue = caseIdOf(decision.getCaseId());
            Wish wish = wishMapper.selectById(caseIdValue);
            if (wish == null || !wish.getUserId().equals(appellantId)) {
                throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "仅被处理作者可申诉");
            }
        }
        // 7 日内
        if (decision.getCreatedAt() != null
                && decision.getCreatedAt().isBefore(LocalDateTime.now(ZoneId.of("UTC")).minus(Duration.ofDays(7)))) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "申诉窗口已关闭（7日）");
        }
        WishAppeal appeal = new WishAppeal();
        appeal.setDecisionId(decisionId);
        appeal.setAppellantId(appellantId);
        appeal.setStatement(statement);
        appeal.setEvidenceRefs(evidenceRefs == null ? null
                : com.cloudmart.wish.util.WishJsonUtils.stringifyList(evidenceRefs));
        appeal.setStatus("PENDING");
        appeal.setVersion(0);
        try {
            appealMapper.insert(appeal);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "该决定已提交过申诉");
        }
        return appeal.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void resolveAppeal(Long appealId, Long reviewerId, boolean accept, String resultReason) {
        WishAppeal appeal = appealMapper.selectById(appealId);
        if (appeal == null || !"PENDING".equals(appeal.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "申诉不存在或已复核");
        }
        ModerationDecision original = decisionMapper.selectById(appeal.getDecisionId());
        if (original != null && reviewerId.equals(original.getActorId())) {
            // 复核人不得是原决定处理人
            throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "原决定处理人不能复核本人决定");
        }
        int affected = appealMapper.update(null, new LambdaUpdateWrapper<WishAppeal>()
                .eq(WishAppeal::getId, appealId)
                .eq(WishAppeal::getVersion, appeal.getVersion())
                .set(WishAppeal::getStatus, accept ? "ACCEPTED" : "REJECTED")
                .set(WishAppeal::getReviewerId, reviewerId)
                .set(WishAppeal::getResultReason, resultReason)
                .setSql("version = version + 1")
                .set(WishAppeal::getResolvedAt, LocalDateTime.now(ZoneId.of("UTC"))));
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "申诉已被并发复核");
        }
        if (accept && original != null) {
            // 通过恢复：仍需检查其他生效下架原因——存在其他活动 case 时不自动恢复
            ModerationCase decided = caseMapper.selectById(original.getCaseId());
            if (decided != null && "WISH".equals(decided.getTargetType())) {
                Long activeCases = caseMapper.selectCount(new LambdaQueryWrapper<ModerationCase>()
                        .eq(ModerationCase::getTargetType, "WISH")
                        .eq(ModerationCase::getTargetId, decided.getTargetId())
                        .in(ModerationCase::getStatus, "OPEN", "IN_REVIEW"));
                if (activeCases == 0) {
                    applyWishDecision(decided.getTargetId(), "RESTORE");
                }
            }
        }
        log.info("申诉复核完成, appealId={}, reviewerId={}, accept={}", appealId, reviewerId, accept);
    }

    public List<WishAppeal> listMyAppeals(Long appellantId, Long cursor, int pageSize) {
        return appealMapper.selectList(new LambdaQueryWrapper<WishAppeal>()
                .eq(WishAppeal::getAppellantId, appellantId)
                .lt(cursor != null, WishAppeal::getId, cursor)
                .orderByDesc(WishAppeal::getId)
                .last("LIMIT " + Math.min(Math.max(pageSize, 1), 50)));
    }

    public List<ModerationCase> listCases(String status, Long cursor, int pageSize) {
        return caseMapper.selectList(new LambdaQueryWrapper<ModerationCase>()
                .eq(status != null && !status.isBlank(), ModerationCase::getStatus,
                        status == null ? null : status.trim().toUpperCase())
                .lt(cursor != null, ModerationCase::getId, cursor)
                .orderByDesc(ModerationCase::getId)
                .last("LIMIT " + Math.min(Math.max(pageSize, 1), 100)));
    }

    // ---------------- 内部 ----------------

    private String targetTypeOf(Long caseId) {
        ModerationCase mc = caseMapper.selectById(caseId);
        return mc != null ? mc.getTargetType() : "";
    }

    private Long caseIdOf(Long caseId) {
        ModerationCase mc = caseMapper.selectById(caseId);
        return mc != null ? mc.getTargetId() : null;
    }

    private com.cloudmart.wish.policy.WishAccessPolicy accessPolicy() {
        return new com.cloudmart.wish.policy.WishAccessPolicy();
    }

    private String normalize(String value, Set<String> allowed, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, label + "不能为空");
        }
        String upper = value.trim().toUpperCase();
        if (!allowed.contains(upper)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "非法" + label + ": " + value);
        }
        return upper;
    }

    /** 活动举报键：type:id:reason 的 SHA-256（ASCII，唯一约束承载合并语义）。 */
    private String dedupKey(String type, Long targetId, String reason) {
        String raw = type + ":" + targetId + ":" + reason;
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
