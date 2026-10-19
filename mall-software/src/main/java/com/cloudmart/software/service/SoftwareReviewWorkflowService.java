package com.cloudmart.software.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * SF08 审核工作流：领取/释放协作标记与历史追溯。
 *
 * <p>不变量：领取只是协作标记，最终决定仍以服务端状态原子执行（SW04 既有路径），
 * 不引入"领取版本号"；领取超时在判定时刻惰性计算（超时后他人可直接抢领），
 * 不新增调度任务；历史内容只读，恢复走 draft-from-history 复制为新 DRAFT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareReviewWorkflowService {

    /** 领取超时：超过此时长未释放，他人可直接抢领（惰性释放） */
    private static final Duration CLAIM_TIMEOUT = Duration.ofHours(1);

    /** 历史行数分页上限（与后台列表一致） */
    private static final int MAX_HISTORY_SIZE = 50;

    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final com.cloudmart.software.repository.SoftwareSourceStateMapper sourceStateMapper;
    private final SoftwareAuditService auditService;
    private final Clock clock;

    /**
     * 领取待审提交（原子抢领）。幂等：本人重复领取返回成功；
     * 他人未超时领取返回 SOFTWARE_REVIEW_CLAIMED；非 PENDING 返回状态错误。
     */
    public Map<String, Object> claim(Long adminId, Long revisionId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LambdaUpdateWrapper<SoftwareRevision> claim = new LambdaUpdateWrapper<SoftwareRevision>()
                .eq(SoftwareRevision::getId, revisionId)
                .eq(SoftwareRevision::getReviewStatus, SoftwareReviewStatus.PENDING)
                .and(w -> w.isNull(SoftwareRevision::getClaimedBy)
                        .or().eq(SoftwareRevision::getClaimedBy, adminId)
                        .or().lt(SoftwareRevision::getClaimedAt, now.minus(CLAIM_TIMEOUT)))
                .set(SoftwareRevision::getClaimedBy, adminId)
                .set(SoftwareRevision::getClaimedAt, now);
        int updated = revisionMapper.update(null, claim);
        if (updated == 0) {
            SoftwareRevision revision = revisionMapper.selectById(revisionId);
            if (revision == null) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "SOFTWARE_NOT_FOUND", "提交不存在");
            }
            if (revision.getReviewStatus() != SoftwareReviewStatus.PENDING) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "SOFTWARE_REVIEW_STATE_INVALID", "该提交已不在待审状态");
            }
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_REVIEW_CLAIMED", "已被其他审核员领取（超时 1 小时后可抢领）");
        }
        SoftwareRevision revision = revisionMapper.selectById(revisionId);
        auditService.record(revision.getSoftwareId(), revisionId, adminId, "ADMIN",
                "REVIEW_CLAIM", "PENDING", "PENDING", null, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revisionId", String.valueOf(revisionId));
        result.put("claimedBy", String.valueOf(adminId));
        result.put("claimedAt", now.toString());
        return result;
    }

    /**
     * 释放领取（仅领取人本人；幂等：未领取/已释放返回成功）。
     */
    public boolean release(Long adminId, Long revisionId) {
        SoftwareRevision revision = revisionMapper.selectById(revisionId);
        if (revision == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_NOT_FOUND", "提交不存在");
        }
        if (revision.getClaimedBy() == null) {
            return true; // 幂等
        }
        if (!Objects.equals(revision.getClaimedBy(), adminId)) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_REVIEW_CLAIMED", "只能释放自己领取的提交");
        }
        int updated = revisionMapper.update(null, new LambdaUpdateWrapper<SoftwareRevision>()
                .eq(SoftwareRevision::getId, revisionId)
                .eq(SoftwareRevision::getClaimedBy, adminId)
                .set(SoftwareRevision::getClaimedBy, null)
                .set(SoftwareRevision::getClaimedAt, null));
        if (updated > 0) {
            auditService.record(revision.getSoftwareId(), revisionId, adminId, "ADMIN",
                    "REVIEW_RELEASE", "PENDING", "PENDING", null, null);
        }
        return true;
    }

    /**
     * 历史内容分页（按提交时间倒序）：只读。每行含与上一提交的字段 diff、
     * 人工决定与来源状态摘要；不展示锁版本或递增修订号。
     */
    public Page<Map<String, Object>> history(Long softwareId, int page, int size) {
        Page<SoftwareRevision> revisionPage = revisionMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_HISTORY_SIZE)),
                new LambdaQueryWrapper<SoftwareRevision>()
                        .eq(SoftwareRevision::getSoftwareId, softwareId)
                        .isNotNull(SoftwareRevision::getSubmittedAt)
                        .orderByDesc(SoftwareRevision::getSubmittedAt)
                        .orderByDesc(SoftwareRevision::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        // 倒序列表：每行与下一条（时间上更早的提交）做 diff——"本行相对上一次提交的变化"
        List<SoftwareRevision> ordered = revisionPage.getRecords();
        for (int i = 0; i < ordered.size(); i++) {
            SoftwareRevision revision = ordered.get(i);
            SoftwareRevision previous = i + 1 < ordered.size() ? ordered.get(i + 1) : null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("revisionId", String.valueOf(revision.getId()));
            row.put("reviewStatus", revision.getReviewStatus().name());
            row.put("submittedAt", String.valueOf(revision.getSubmittedAt()));
            row.put("reviewedAt", revision.getReviewedAt() == null
                    ? null : String.valueOf(revision.getReviewedAt()));
            row.put("reviewerId", revision.getReviewerId() == null
                    ? null : String.valueOf(revision.getReviewerId()));
            row.put("reviewReason", revision.getReviewReason());
            row.put("claimedBy", revision.getClaimedBy() == null
                    ? null : String.valueOf(revision.getClaimedBy()));
            // 与上一提交的字段 diff（文本字段；HTML 正文只比变化标志避免大负载）
            row.put("fieldDiff", diffAgainst(previous, revision));
            row.put("sourceStates", sourceStates(softwareId, revision.getId()));
            rows.add(row);
        }
        Page<Map<String, Object>> result = new Page<>(revisionPage.getCurrent(),
                revisionPage.getSize(), revisionPage.getTotal());
        result.setRecords(rows);
        return result;
    }

    /** 文本字段与上一提交对比：仅返回发生变化的字段（null=无变化或无上一条） */
    private List<Map<String, Object>> diffAgainst(SoftwareRevision previous,
                                                  SoftwareRevision current) {
        if (previous == null) {
            return List.of();
        }
        List<Map<String, Object>> diff = new ArrayList<>();
        record TextField(String name, String before, String after) {
        }
        List<TextField> fields = List.of(
                new TextField("name", previous.getName(), current.getName()),
                new TextField("summary", previous.getSummary(), current.getSummary()),
                new TextField("developerName", previous.getDeveloperName(),
                        current.getDeveloperName()),
                new TextField("officialUrl", previous.getOfficialUrl(),
                        current.getOfficialUrl()),
                new TextField("systemRequirements", previous.getSystemRequirements(),
                        current.getSystemRequirements()),
                new TextField("installationGuide", previous.getInstallationGuide(),
                        current.getInstallationGuide()),
                new TextField("knownIssues", previous.getKnownIssues(),
                        current.getKnownIssues()),
                new TextField("descriptionHtml", previous.getDescriptionHtml(),
                        current.getDescriptionHtml()));
        for (TextField field : fields) {
            if (!Objects.equals(field.before(), field.after())) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("field", field.name());
                entry.put("changed", true);
                diff.add(entry);
            }
        }
        return diff;
    }

    /** 来源状态摘要（来源行 + source_state 可用性；不含 URL 与凭据） */
    private List<Map<String, Object>> sourceStates(Long softwareId, Long revisionId) {
        List<SoftwareRelease> releases = releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, revisionId));
        if (releases.isEmpty()) {
            return List.of();
        }
        List<SoftwareSource> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<SoftwareSource>()
                        .in(SoftwareSource::getReleaseId,
                                releases.stream().map(SoftwareRelease::getId).toList()));
        // SF08：可用性事实在 source_state（按 softwareId+sourceKey 关联）
        Map<String, com.cloudmart.software.entity.SoftwareSourceState> states = new LinkedHashMap<>();
        if (!sources.isEmpty()) {
            for (var state : sourceStateMapper.selectList(
                    new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareSourceState>()
                            .eq(com.cloudmart.software.entity.SoftwareSourceState::getSoftwareId,
                                    softwareId)
                            .in(com.cloudmart.software.entity.SoftwareSourceState::getSourceKey,
                                    sources.stream().map(SoftwareSource::getSourceKey).toList()))) {
                states.put(state.getSourceKey(), state);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SoftwareSource source : sources) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceKey", source.getSourceKey());
            row.put("provider", source.getProvider() == null ? null : source.getProvider().name());
            row.put("platform", source.getPlatform() == null ? null : source.getPlatform().name());
            var state = states.get(source.getSourceKey());
            row.put("availability", state == null || state.getAvailability() == null
                    ? "UNKNOWN" : state.getAvailability().name());
            row.put("disabled", state != null
                    && state.getAvailability()
                            == com.cloudmart.software.enums.SourceAvailability.DISABLED);
            rows.add(row);
        }
        return rows;
    }
}
