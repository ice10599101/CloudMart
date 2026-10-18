package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareLinkFeedback;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.LinkFeedbackStatus;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.mq.SoftwareEvents;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareLinkFeedbackMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.service.SoftwareRateLimiter;
import com.cloudmart.software.service.SoftwareSourceFeedbackService;
import com.cloudmart.software.vo.software.SoftwareFeedbackVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/**
 * 链接失效反馈实现（方案 5.3/6.9）。
 *
 * <p>规则：每用户每来源指纹一条开放反馈（唯一键幂等，重复提交返回现有反馈）；
 * 用户反馈不是最终检测结果；24 小时内同指纹 3 个不同账号 → SUSPECTED（仍可获取）
 * 并写 Outbox 通知分享者；仅管理员能判 INVALID/DISABLED；修复通过新快照上线后
 * 由管理员将旧反馈标记 RESOLVED 保留 original 指纹与修复 revisionId。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareSourceFeedbackServiceImpl implements SoftwareSourceFeedbackService {

    private static final Set<String> REASON_CODES =
            Set.of("LINK_INVALID", "CODE_WRONG", "FILE_MISMATCH", "SUSPICIOUS", "OTHER");
    private static final int SCHEMA_VERSION = 1;
    private static final long REPORT_WINDOW_HOURS = 24;

    private final SoftwareAccessPolicy accessPolicy;
    private final SoftwareMapper softwareMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final SoftwareSourceStateMapper sourceStateMapper;
    private final SoftwareLinkFeedbackMapper feedbackMapper;
    private final SoftwareProperties softwareProperties;
    private final SoftwareRateLimiter rateLimiter;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public SoftwareFeedbackVO feedback(Long userId, Long softwareId, Long sourceId,
                                       String reasonCode, String description) {
        if (reasonCode == null || !REASON_CODES.contains(reasonCode)) {
            throw new BusinessException("SOFTWARE_FEEDBACK_INVALID",
                    "反馈类型须为 LINK_INVALID/CODE_WRONG/FILE_MISMATCH/SUSPICIOUS/OTHER");
        }
        if (description != null && description.length() > 500) {
            throw new BusinessException("SOFTWARE_FEEDBACK_INVALID", "补充说明最长 500 字");
        }
        // 11.2 限频：单用户每日失效反馈上限（重复提交同指纹去重另有兜底）
        if (!rateLimiter.allowFeedback(userId)) {
            throw new BusinessException("SOFTWARE_RATE_LIMITED", "反馈过于频繁，请稍后再试");
        }
        // 宿主可见性（评论/反馈同谓词）
        SoftwareRevision published = accessPolicy.visiblePublishedRevision(softwareId);
        if (published == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        // 来源归属链：必须属于当前线上快照（旧快照来源反馈无意义，提示刷新）
        SoftwareSource source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        SoftwareRelease release = releaseMapper.selectById(source.getReleaseId());
        if (release == null || !release.getRevisionId().equals(published.getId())) {
            throw new BusinessException("SOFTWARE_SOURCE_STALE", "来源列表已更新，请刷新后重新反馈");
        }

        String fingerprint = source.getContentFingerprint();
        // 同指纹同人去重：重复提交返回现有反馈（5.3）
        SoftwareLinkFeedback existing = feedbackMapper.selectOne(
                new LambdaQueryWrapper<SoftwareLinkFeedback>()
                        .eq(SoftwareLinkFeedback::getReporterId, userId)
                        .eq(SoftwareLinkFeedback::getSoftwareId, softwareId)
                        .eq(SoftwareLinkFeedback::getSourceKey, source.getSourceKey())
                        .eq(SoftwareLinkFeedback::getSourceFingerprint, fingerprint)
                        .in(SoftwareLinkFeedback::getStatus,
                                LinkFeedbackStatus.OPEN, LinkFeedbackStatus.VERIFYING));
        if (existing != null) {
            return new SoftwareFeedbackVO(existing.getId(), existing.getSourceKey(),
                    existing.getReasonCode(), existing.getStatus().name());
        }

        SoftwareLinkFeedback feedback = new SoftwareLinkFeedback();
        feedback.setSoftwareId(softwareId);
        feedback.setSourceKey(source.getSourceKey());
        feedback.setSourceIdSnapshot(source.getId());
        feedback.setSourceFingerprint(fingerprint);
        feedback.setReporterId(userId);
        feedback.setReasonCode(reasonCode);
        feedback.setDescription(description);
        feedback.setStatus(LinkFeedbackStatus.OPEN);
        feedback.setLockVersion(0);
        feedbackMapper.insert(feedback);

        raiseSuspectedIfNeeded(softwareId, source.getSourceKey(), fingerprint);

        return new SoftwareFeedbackVO(feedback.getId(), source.getSourceKey(),
                reasonCode, feedback.getStatus().name());
    }

    /**
     * 24h 内同指纹 3 个不同账号 → SUSPECTED（仍可获取）+ Outbox 通知分享者（5.3/SW07）。
     *
     * <p>SW07：抬升必须走带状态条件的原子更新——人工 INVALID/DISABLED 结论优先，
     * 自动反馈绝不降级；只有真正发生一次 UNKNOWN/AVAILABLE→SUSPECTED 迁移才通知作者，
     * 后续反馈只累计统计。读取→无条件写覆盖并发人工处置的竞态由此消除。</p>
     */
    private void raiseSuspectedIfNeeded(Long softwareId, String sourceKey, String fingerprint) {
        LocalDateTime since = LocalDateTime.now(clock).minusHours(REPORT_WINDOW_HOURS);
        long distinctReporters = feedbackMapper.countRecentDistinctReporters(
                softwareId, sourceKey, fingerprint, since);
        if (distinctReporters < softwareProperties.getSuspectedDistinctReporters()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String reason = "24小时内收到 " + distinctReporters + " 个不同账号的失效反馈，待管理员核实";
        if (sourceStateMapper.raiseSuspectedIfEligible(softwareId, sourceKey, fingerprint, now, reason) > 0) {
            notifyAuthor(softwareId, sourceKey, distinctReporters);
            return;
        }
        // 未迁移：状态行已存在（SUSPECTED/INVALID/DISABLED）——只累计，不重复通知
        if (sourceStateMapper.selectCount(new LambdaQueryWrapper<SoftwareSourceState>()
                .eq(SoftwareSourceState::getSoftwareId, softwareId)
                .eq(SoftwareSourceState::getSourceKey, sourceKey)) > 0) {
            return;
        }
        // 首次出现该来源键：插入 SUSPECTED；并发插入冲突说明他方已迁移，由其负责通知
        SoftwareSourceState state = new SoftwareSourceState();
        state.setSoftwareId(softwareId);
        state.setSourceKey(sourceKey);
        state.setAvailability(SourceAvailability.SUSPECTED);
        state.setCheckedFingerprint(fingerprint);
        state.setCheckedAt(now);
        state.setReason(reason);
        state.setLockVersion(0);
        try {
            sourceStateMapper.insert(state);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return;
        }
        notifyAuthor(softwareId, sourceKey, distinctReporters);
    }

    /** 分享者通知事件（S10 消费投递）：载荷只含标识，不含链接/提取码/反馈者身份 */
    private void notifyAuthor(Long softwareId, String sourceKey, long distinctReporters) {
        Software software = softwareMapper.selectById(softwareId);
        if (software == null || software.getOwnerId() == null) {
            return;
        }
        Long authorId = software.getOwnerId();
        outboxService.record(EventEnvelope.of(SoftwareEvents.TAG_SOURCE_REPORTED, SCHEMA_VERSION,
                String.valueOf(softwareId), System.currentTimeMillis(), null,
                objectMapper.writeValueAsString(Map.of(
                        "softwareId", String.valueOf(softwareId),
                        "authorId", String.valueOf(authorId),
                        "sourceKey", sourceKey,
                        "distinctReporters", String.valueOf(distinctReporters)))));
        log.info("来源标记 SUSPECTED 并通知分享者 softwareId={} sourceKey={} reporters={}",
                softwareId, sourceKey, distinctReporters);
    }
}
