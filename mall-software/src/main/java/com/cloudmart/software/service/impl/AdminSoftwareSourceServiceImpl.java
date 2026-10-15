package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.service.AdminSoftwareSourceService;
import com.cloudmart.software.service.SoftwareAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * 管理员来源状态处置实现（方案 4.3/6.6）。
 *
 * <p>键 (software_id, source_key)；核实类结论（AVAILABLE/SUSPECTED/INVALID）按
 * 当前线上快照中该来源的指纹应用（跨快照继承），指纹随新快照变更则展示 UNKNOWN；
 * DISABLED 无条件生效且允许对当前快照无此来源的 key 设置（禁用继承）；
 * 解除禁用=置回 UNKNOWN（由后续核实驱动）。处置写软件域审计日志。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminSoftwareSourceServiceImpl implements AdminSoftwareSourceService {

    private static final Set<String> ALLOWED_STATES =
            Set.of("UNKNOWN", "AVAILABLE", "SUSPECTED", "INVALID", "DISABLED");

    private final SoftwareAccessPolicy accessPolicy;
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

        // 当前线上快照中该稳定键的来源（可能已被作者替换删除：禁用仍允许，核实类拒绝）
        SoftwareSource current = sourceMapper.selectOne(
                new LambdaQueryWrapper<SoftwareSource>()
                        .eq(SoftwareSource::getReleaseId, published.getId())
                        .eq(SoftwareSource::getSourceKey, sourceKey)
                        .last("LIMIT 1"));
        String checkedFingerprint;
        if (current != null) {
            checkedFingerprint = current.getContentFingerprint();
        } else if (target == SourceAvailability.DISABLED || target == SourceAvailability.UNKNOWN) {
            // 来源已从当前快照移除：禁用记录保留（复制快照继承），核实类无从谈起
            checkedFingerprint = null;
        } else {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "该来源不在当前线上快照中，无法核实");
        }

        SoftwareSourceState state = sourceStateMapper.selectOne(
                new LambdaQueryWrapper<SoftwareSourceState>()
                        .eq(SoftwareSourceState::getSoftwareId, softwareId)
                        .eq(SoftwareSourceState::getSourceKey, sourceKey));
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
        auditService.record(softwareId, published.getId(), null, adminId, "ADMIN",
                target == SourceAvailability.DISABLED ? "SOURCE_DISABLE" : "SOURCE_VERIFY",
                null, target.name(), reason, null);
        return true;
    }
}
