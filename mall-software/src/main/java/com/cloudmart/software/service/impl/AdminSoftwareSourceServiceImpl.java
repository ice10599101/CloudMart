package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.service.AdminSoftwareSourceService;
import com.cloudmart.software.service.SoftwareAuditService;
import com.cloudmart.software.vo.software.AdminSoftwareSourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员来源状态处置实现（方案 4.3/6.6）。
 *
 * <p>键 (software_id, source_key)；核实类结论（AVAILABLE/SUSPECTED/INVALID）按
 * 当前线上快照中该来源的指纹应用（跨快照继承），指纹随新快照变更则展示 UNKNOWN；
 * DISABLED 无条件生效且允许对当前快照无此来源的 key 设置（禁用继承）；
 * 解除禁用=置回 UNKNOWN（由后续核实驱动）。处置写软件域审计日志。</p>
 *
 * <p>SW06：来源关联必须走 source.release_id → software_release.id → release.revision_id，
 * 不能把 revisionId 直接当 releaseId 比较；重复稳定键视为数据异常而非 LIMIT 1 掩盖。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminSoftwareSourceServiceImpl implements AdminSoftwareSourceService {

    private static final Set<String> ALLOWED_STATES =
            Set.of("UNKNOWN", "AVAILABLE", "SUSPECTED", "INVALID", "DISABLED");

    private final SoftwareAccessPolicy accessPolicy;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final SoftwareSourceStateMapper sourceStateMapper;
    private final SoftwareAuditService auditService;
    private final Clock clock;

    @Override
    @Transactional
    public boolean applyState(Long adminId, Long softwareId, String sourceKey,
                              String availability, String reason) {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new BusinessException("SOFTWARE_SOURCE_STATE_INVALID", "sourceKey 不能为空");
        }
        if (availability == null || !ALLOWED_STATES.contains(availability.toUpperCase(Locale.ROOT))) {
            throw new BusinessException("SOFTWARE_SOURCE_STATE_INVALID",
                    "availability 须为 UNKNOWN/AVAILABLE/SUSPECTED/INVALID/DISABLED");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new BusinessException("SOFTWARE_SOURCE_STATE_INVALID", "处置原因须为 1~500 字");
        }
        SourceAvailability target = SourceAvailability.valueOf(availability.toUpperCase(Locale.ROOT));
        SoftwareRevision published = accessPolicy.visiblePublishedRevision(softwareId);
        if (published == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }

        SoftwareSource current = findCurrentPublishedSource(softwareId, published, sourceKey);
        String checkedFingerprint;
        if (current != null) {
            checkedFingerprint = current.getContentFingerprint();
        } else if (target == SourceAvailability.DISABLED || target == SourceAvailability.UNKNOWN) {
            // 来源已从当前发布内容移除：只允许明确的禁用/清理动作，核实类无从谈起
            checkedFingerprint = null;
        } else {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "该来源不在当前线上快照中，无法核实");
        }

        SoftwareSourceState state = sourceStateMapper.selectOne(
                new LambdaQueryWrapper<SoftwareSourceState>()
                        .eq(SoftwareSourceState::getSoftwareId, softwareId)
                        .eq(SoftwareSourceState::getSourceKey, sourceKey));
        String fromState = state == null || state.getAvailability() == null
                ? "NONE" : state.getAvailability().name();
        if (state == null) {
            state = new SoftwareSourceState();
            state.setSoftwareId(softwareId);
            state.setSourceKey(sourceKey);
            state.setLockVersion(0);
        }
        state.setAvailability(target);
        state.setCheckedFingerprint(checkedFingerprint);
        state.setCheckedAt(LocalDateTime.now(clock));
        state.setCheckedBy(adminId);
        state.setReason(reason);
        if (state.getId() == null) {
            sourceStateMapper.insert(state);
        } else {
            state.setLockVersion(state.getLockVersion() + 1);
            sourceStateMapper.updateById(state);
        }
        auditService.record(softwareId, published.getId(), adminId, "ADMIN",
                target == SourceAvailability.DISABLED ? "SOURCE_DISABLE" : "SOURCE_VERIFY",
                fromState, target.name(),
                "sourceKey=" + sourceKey
                        + " fingerprint=" + (checkedFingerprint == null ? "-" : checkedFingerprint)
                        + " reason=" + reason,
                null);
        return true;
    }

    /**
     * 当前发布快照中该稳定键的来源：单次联结语义（revision → release → source）。
     * 重复稳定键说明数据或历史写入异常，直接报错，不能用 LIMIT 1 随机选一条。
     */
    private SoftwareSource findCurrentPublishedSource(Long softwareId,
                                                      SoftwareRevision published,
                                                      String sourceKey) {
        List<Long> releaseIds = releaseMapper.selectList(
                        new LambdaQueryWrapper<SoftwareRelease>()
                                .eq(SoftwareRelease::getRevisionId, published.getId()))
                .stream().map(SoftwareRelease::getId).toList();
        if (releaseIds.isEmpty()) {
            return null;
        }
        List<SoftwareSource> matches = sourceMapper.selectList(
                new LambdaQueryWrapper<SoftwareSource>()
                        .in(SoftwareSource::getReleaseId, releaseIds)
                        .eq(SoftwareSource::getSourceKey, sourceKey));
        if (matches.size() > 1) {
            throw new BusinessException("SOFTWARE_SOURCE_STATE_INVALID",
                    "软件 " + softwareId + " 当前发布快照存在重复 sourceKey=" + sourceKey
                            + "，请先修复数据再处置");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    @Override
    public List<AdminSoftwareSourceVO> listSources(Long softwareId) {
        SoftwareRevision published = accessPolicy.visiblePublishedRevision(softwareId);
        if (published == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        List<SoftwareRelease> releases = releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, published.getId())
                        .orderByAsc(SoftwareRelease::getReleaseOrder)
                        .orderByAsc(SoftwareRelease::getId));
        if (releases.isEmpty()) {
            return List.of();
        }
        Map<Long, SoftwareRelease> releaseById = releases.stream()
                .collect(Collectors.toMap(SoftwareRelease::getId, Function.identity()));
        List<SoftwareSource> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<SoftwareSource>()
                        .in(SoftwareSource::getReleaseId, releaseById.keySet())
                        .orderByAsc(SoftwareSource::getDisplayOrder)
                        .orderByAsc(SoftwareSource::getId));
        Map<String, SoftwareSourceState> stateByKey = sourceStateMapper.selectList(
                        new LambdaQueryWrapper<SoftwareSourceState>()
                                .eq(SoftwareSourceState::getSoftwareId, softwareId))
                .stream()
                .collect(Collectors.toMap(SoftwareSourceState::getSourceKey,
                        Function.identity(), (a, b) -> a));

        List<AdminSoftwareSourceVO> result = new ArrayList<>(sources.size());
        for (SoftwareSource source : sources) {
            SoftwareRelease release = releaseById.get(source.getReleaseId());
            if (release == null) {
                continue;
            }
            SoftwareSourceState state = stateByKey.get(source.getSourceKey());
            result.add(new AdminSoftwareSourceVO(
                    source.getId(),
                    release.getReleaseKey(),
                    release.getVersionName(),
                    release.getChannel() == null ? null : release.getChannel().name(),
                    source.getSourceKey(),
                    source.getSourceType() == null ? null : source.getSourceType().name(),
                    source.getPlatform() == null ? null : source.getPlatform().name(),
                    source.getArch() == null ? null : source.getArch().name(),
                    source.getLabel(),
                    maskUrl(source.getUrl()),
                    state == null || state.getAvailability() == null
                            ? "UNKNOWN" : state.getAvailability().name(),
                    state == null ? null : state.getReason(),
                    state == null ? null : state.getCheckedFingerprint(),
                    state == null ? null : state.getCheckedAt()));
        }
        return result;
    }

    /** 列表默认脱敏：保留协议与主机，隐藏路径/查询串；提取码与解压密码根本不进入视图 */
    private String maskUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.startsWith("magnet:")) {
            return "magnet:?xt=urn:btih:***";
        }
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return "***";
        }
        int pathStart = url.indexOf('/', scheme + 3);
        return (pathStart < 0 ? url : url.substring(0, pathStart)) + "/***";
    }
}
