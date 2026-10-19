package com.cloudmart.software.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourcePatrol;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareSourceType;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourcePatrolMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SF07 来源健康巡检：仅自动检查明确允许的 HTTPS 来源（HTTPS_DOWNLOAD/OFFICIAL_PAGE/
 * WEB_APP）；磁力/种子和需要登录的网盘不伪装成"已检查可下载"。
 *
 * <p>不变量：巡检结论写 {@code software_source_state} 时不覆盖人工 DISABLED/INVALID；
 * 限频/验证码/403 优先 UNKNOWN 不判死链；DNS 解析失败/连接拒绝才可判 INVALID；
 * 预算（单轮条数）由配置控制，调度与运行日志由现有任务设施托管，本服务只提供业务入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareSourcePatrolService {

    /** 允许自动巡检的来源类型（SF01 官方 HTTPS 三类） */
    private static final Set<SoftwareSourceType> PATROL_TYPES = Set.of(
            SoftwareSourceType.HTTPS_DOWNLOAD,
            SoftwareSourceType.OFFICIAL_PAGE,
            SoftwareSourceType.WEB_APP);

    /** 默认单轮预算（可用配置覆盖） */
    private static final int DEFAULT_BATCH_BUDGET = 50;

    /** 退避：AVAILABLE 24h；UNKNOWN 6h；INVALID 12h（人工优先，到期后仍不覆盖） */
    private static final Map<String, java.time.Duration> NEXT_CHECK = Map.of(
            "AVAILABLE", java.time.Duration.ofHours(24),
            "UNKNOWN", java.time.Duration.ofHours(6),
            "INVALID", java.time.Duration.ofHours(12));

    private final SoftwareSourceMapper sourceMapper;
    private final SoftwareSourceStateMapper sourceStateMapper;
    private final SoftwareSourcePatrolMapper patrolMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareMapper softwareMapper;
    private final SoftwareHttpProbe httpProbe;
    private final SoftwareProperties softwareProperties;
    private final Clock clock;

    /** 巡检一轮（预算内挑选最久未检的 HTTPS 来源）；返回处理条数（供任务日志） */
    public int runOnce() {
        int budget = Math.max(1, Math.min(DEFAULT_BATCH_BUDGET, 200));
        List<SoftwareSource> candidates = pickBatch(budget);
        int handled = 0;
        for (SoftwareSource source : candidates) {
            try {
                patrolSource(source);
                handled++;
            } catch (Exception e) {
                log.warn("巡检单条失败 sourceId={}: {}", source.getId(), e.getMessage());
            }
        }
        log.info("SF07 来源巡检一轮完成：预算 {} 实际 {}", budget, handled);
        return handled;
    }

    /** 管理员手动重检（立即探测单条；同样不覆盖人工结论） */
    public Map<String, Object> patrolById(Long sourceId) {
        SoftwareSource source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_NOT_FOUND", "来源不存在");
        }
        if (!PATROL_TYPES.contains(source.getSourceType())) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_PATROL_NOT_SUPPORTED", "该来源类型不支持自动巡检（仅官方 HTTPS 来源）");
        }
        return patrolSource(source);
    }

    /**
     * 挑选预算内待检来源：当前线上快照的 HTTPS 类来源，人工未禁用/未判死，
     * 按最近巡检时间最早优先（从未巡检优先）。
     */
    private List<SoftwareSource> pickBatch(int budget) {
        // 线上快照的 HTTPS 来源（软件必须 ACTIVE 且已发布）
        List<SoftwareSource> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<SoftwareSource>()
                        .in(SoftwareSource::getSourceType, PATROL_TYPES)
                        .last("LIMIT 500"));
        if (sources.isEmpty()) {
            return List.of();
        }
        List<Candidate> eligible = new ArrayList<>();
        for (SoftwareSource source : sources) {
            // 来源无 software_id 列：经 release→revision→software 解析归属与线上快照
            SoftwareRelease release = releaseMapper.selectById(source.getReleaseId());
            if (release == null) {
                continue;
            }
            SoftwareRevision revision = revisionMapper.selectById(release.getRevisionId());
            if (revision == null
                    || revision.getReviewStatus() != SoftwareReviewStatus.APPROVED) {
                continue;
            }
            Software software = softwareMapper.selectById(revision.getSoftwareId());
            if (software == null || software.getDeletedAt() != null
                    || software.getLifecycle() != SoftwareLifecycle.ACTIVE
                    || software.getPublishedRevisionId() == null
                    || !software.getPublishedRevisionId().equals(revision.getId())) {
                continue; // 仅巡检 ACTIVE 软件的当前线上快照来源
            }
            SoftwareSourceState state = sourceStateMapper.selectOne(
                    new LambdaQueryWrapper<SoftwareSourceState>()
                            .eq(SoftwareSourceState::getSoftwareId, software.getId())
                            .eq(SoftwareSourceState::getSourceKey, source.getSourceKey()));
            if (state != null && (state.getAvailability() == SourceAvailability.DISABLED
                    || state.getAvailability() == SourceAvailability.INVALID)) {
                continue; // 人工结论不参与自动巡检（也不被覆盖）
            }
            eligible.add(new Candidate(source, software.getId()));
        }
        if (eligible.size() <= budget) {
            return eligible.stream().map(Candidate::source).toList();
        }
        // 预算裁剪：按最近巡检时间升序（从未巡检时间视为极早）
        eligible.sort((a, b) -> lastCheckedAt(a.source()).compareTo(lastCheckedAt(b.source())));
        return eligible.subList(0, budget).stream().map(Candidate::source).toList();
    }

    /** 巡检候选：来源行 + 解析出的软件归属（source 表无 software_id 列） */
    private record Candidate(SoftwareSource source, Long softwareId) {
    }

    private LocalDateTime lastCheckedAt(SoftwareSource source) {
        SoftwareSourcePatrol latest = latestPatrolBySourceKey(source.getSourceKey());
        return latest == null ? epoch() : latest.getCheckedAt();
    }

    private LocalDateTime epoch() {
        return LocalDateTime.now(clock).minusYears(1);
    }

    /** 探测单条并落库（巡检记录 + 来源状态联动）；返回业务结果 */
    private Map<String, Object> patrolSource(SoftwareSource source) {
        // 归属解析（source 表无 software_id 列）：release→revision→software
        SoftwareRelease release = releaseMapper.selectById(source.getReleaseId());
        if (release == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_NOT_FOUND", "来源所属版本不存在");
        }
        SoftwareRevision revision = revisionMapper.selectById(release.getRevisionId());
        if (revision == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_NOT_FOUND", "来源所属快照不存在");
        }
        Long softwareId = revision.getSoftwareId();
        URI uri = URI.create(source.getUrl());
        SoftwareHttpProbe.ProbeResult probe = httpProbe.probe(source.getUrl());
        LocalDateTime now = LocalDateTime.now(clock);
        java.time.Duration backoff = NEXT_CHECK.getOrDefault(probe.outcome().name(),
                java.time.Duration.ofHours(6));

        SoftwareSourcePatrol record = new SoftwareSourcePatrol();
        record.setSoftwareId(softwareId);
        record.setSourceKey(source.getSourceKey());
        record.setSourceFingerprint(source.getContentFingerprint());
        record.setSourceType(source.getSourceType());
        record.setUrlHost(uri.getHost() == null ? "unknown" : uri.getHost());
        record.setOutcome(probe.outcome().name());
        record.setReasonCode(probe.reasonCode());
        record.setHttpStatus(probe.httpStatus());
        record.setRedirectCount(probe.redirectCount());
        record.setElapsedMs((int) probe.elapsedMs());
        record.setCheckedAt(now);
        record.setNextCheckAt(now.plus(backoff));
        record.setCreatedAt(now);
        patrolMapper.insert(record);

        // 联动来源状态：仅当当前状态允许自动结论覆盖（UNKNOWN/SUSPECTED 且非人工）
        if (probe.outcome() != SoftwareHttpProbe.Outcome.UNKNOWN) {
            SoftwareSourceState state = sourceStateMapper.selectOne(
                    new LambdaQueryWrapper<SoftwareSourceState>()
                            .eq(SoftwareSourceState::getSoftwareId, softwareId)
                            .eq(SoftwareSourceState::getSourceKey, source.getSourceKey()));
            SourceAvailability target = probe.outcome() == SoftwareHttpProbe.Outcome.AVAILABLE
                    ? SourceAvailability.AVAILABLE : SourceAvailability.INVALID;
            if (state == null) {
                SoftwareSourceState created = new SoftwareSourceState();
                created.setSoftwareId(softwareId);
                created.setSourceKey(source.getSourceKey());
                created.setAvailability(target);
                created.setCheckedFingerprint(source.getContentFingerprint());
                created.setCheckedAt(now);
                created.setReason("自动巡检：" + probe.reasonCode());
                sourceStateMapper.insert(created);
            } else if (state.getAvailability() == SourceAvailability.UNKNOWN
                    || state.getAvailability() == SourceAvailability.SUSPECTED
                    || state.getAvailability() == SourceAvailability.AVAILABLE) {
                // 可用→可用/失效可更新；已人工 INVALID/DISABLED 不进入该分支
                sourceStateMapper.update(null, new LambdaUpdateWrapper<SoftwareSourceState>()
                        .eq(SoftwareSourceState::getId, state.getId())
                        .set(SoftwareSourceState::getAvailability, target)
                        .set(SoftwareSourceState::getCheckedFingerprint,
                                source.getContentFingerprint())
                        .set(SoftwareSourceState::getCheckedAt, now)
                        .set(SoftwareSourceState::getReason, "自动巡检：" + probe.reasonCode()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceId", String.valueOf(source.getId()));
        result.put("outcome", probe.outcome().name());
        result.put("reasonCode", probe.reasonCode());
        result.put("nextCheckAt", record.getNextCheckAt().toString());
        return result;
    }

    private SoftwareSourcePatrol latestPatrolBySourceKey(String sourceKey) {
        List<SoftwareSourcePatrol> rows = patrolMapper.selectList(
                new LambdaQueryWrapper<SoftwareSourcePatrol>()
                        .eq(SoftwareSourcePatrol::getSourceKey, sourceKey)
                        .orderByDesc(SoftwareSourcePatrol::getCheckedAt)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SoftwareSourcePatrol latestPatrol(Long softwareId, String sourceKey) {
        List<SoftwareSourcePatrol> rows = patrolMapper.selectList(
                new LambdaQueryWrapper<SoftwareSourcePatrol>()
                        .eq(SoftwareSourcePatrol::getSoftwareId, softwareId)
                        .eq(SoftwareSourcePatrol::getSourceKey, sourceKey)
                        .orderByDesc(SoftwareSourcePatrol::getCheckedAt)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }
}
