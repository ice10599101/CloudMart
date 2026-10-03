package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.entity.PetReport;
import com.cloudmart.pet.entity.PetUserSanction;
import com.cloudmart.pet.repository.PetUserSanctionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * R05 宠物域访问策略：处罚事实（pet_user_sanction）的创建、查询与解除。
 *
 * <p>语义（§6.3/§8.2）：</p>
 * <ul>
 *   <li>封禁只限制宠物模块的新写入（scope 判定），保留查看资产、历史和已获得奖励的领取；</li>
 *   <li>同一举报的同一动作只生成一条处罚（uk source_report_id+action 幂等，重复处置不重罚）；</li>
 *   <li>状态机 ACTIVE → EXPIRED（到期）/ REVOKED（人工解除，理由必填留痕）；</li>
 *   <li>当前轮不新增全站账号封禁能力——需要时调用账户域并跟踪结果。</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetAccessPolicy {

    /** 处罚范围 */
    public static final String SCOPE_SOCIAL_MUTE = "SOCIAL_MUTE";
    public static final String SCOPE_PUBLIC_CONTENT_DISABLED = "PUBLIC_CONTENT_DISABLED";

    private final PetUserSanctionMapper sanctionMapper;

    /** 社交写入限制（留言墙/关系/对战邀请/合作邀请） */
    public static boolean isSocialMuteScope(String scope) {
        return SCOPE_SOCIAL_MUTE.equals(scope);
    }

    /**
     * 处罚是否生效（R05 统一判定入口）：ACTIVE 且未到期；到期按时间惰性流转 EXPIRED。
     * 业务写入口在拒绝前调用本方法并惰性落到期状态（读路径幂等，重复流转无副作用）。
     */
    @Transactional
    public boolean isRestricted(Long userId, String scope) {
        List<PetUserSanction> active = sanctionMapper.selectList(
                new LambdaQueryWrapper<PetUserSanction>()
                        .eq(PetUserSanction::getUserId, userId)
                        .eq(PetUserSanction::getScope, scope)
                        .eq(PetUserSanction::getStatus, "ACTIVE"));
        if (active.isEmpty()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        boolean restricted = false;
        for (PetUserSanction sanction : active) {
            if (sanction.getExpiresAt() != null && !sanction.getExpiresAt().isAfter(now)) {
                // 到期惰性流转（条件更新，幂等）
                sanctionMapper.update(null, new LambdaUpdateWrapper<PetUserSanction>()
                        .set(PetUserSanction::getStatus, "EXPIRED")
                        .eq(PetUserSanction::getId, sanction.getId())
                        .eq(PetUserSanction::getStatus, "ACTIVE"));
                log.info("处罚到期自动解除: sanctionId={}, userId={}, scope={}",
                        sanction.getId(), userId, scope);
                continue;
            }
            restricted = true;
        }
        return restricted;
    }

    /** @return 被处罚用户社交写入被限制（R05：各写入口拒绝前调用） */
    @Transactional
    public boolean isSociallyMuted(Long userId) {
        return isRestricted(userId, SCOPE_SOCIAL_MUTE);
    }

    /**
     * 处置动作 → 处罚事实（resolve 事务内调用；动作→范围映射见 §8.2 处置矩阵）：
     * WARN → 一次警告记录（不限制写入，通知被处置用户由通知链路承担）；
     * PET_BAN → SOCIAL_MUTE + PUBLIC_CONTENT_DISABLED（限时或人工解除）。
     *
     * @return 处罚记录；同举报同动作重复处置返回既有记录（幂等，不重复生成）
     */
    @Transactional
    public PetUserSanction issueSanction(PetReport report, String action, Long targetUserId,
                                         int durationSeconds, String reason, Long operatorId) {
        // 幂等：同举报同动作已有处罚 → 返回既有记录（重复处置不重罚）
        PetUserSanction existing = sanctionMapper.selectOne(new LambdaQueryWrapper<PetUserSanction>()
                .eq(PetUserSanction::getSourceReportId, report.getId())
                .eq(PetUserSanction::getAction, action)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        PetUserSanction sanction = new PetUserSanction();
        sanction.setUserId(targetUserId);
        sanction.setStatus("ACTIVE");
        sanction.setStartsAt(LocalDateTime.now(ZoneOffset.UTC));
        sanction.setSourceReportId(report.getId());
        sanction.setAction(action);
        sanction.setReason(reason);
        sanction.setOperatorId(operatorId);
        switch (action) {
            case "USER_WARNED" -> {
                // 警告：仅记录事实（不限制写入）
                sanction.setScope("WARN");
                try {
                    sanctionMapper.insert(sanction);
                } catch (DuplicateKeyException e) {
                    return existingOrThrow(report.getId(), action);
                }
            }
            case "USER_PET_BANNED" -> {
                // 封禁：社交写入 + 公开内容两条范围（封禁不动全站账号）
                sanction.setScope(SCOPE_SOCIAL_MUTE);
                if (durationSeconds > 0) {
                    sanction.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(durationSeconds));
                }
                try {
                    sanctionMapper.insert(sanction);
                } catch (DuplicateKeyException e) {
                    return existingOrThrow(report.getId(), action);
                }
                PetUserSanction content = new PetUserSanction();
                content.setUserId(targetUserId);
                content.setScope(SCOPE_PUBLIC_CONTENT_DISABLED);
                content.setStatus("ACTIVE");
                content.setStartsAt(sanction.getStartsAt());
                content.setExpiresAt(sanction.getExpiresAt());
                content.setSourceReportId(report.getId());
                content.setAction(action);
                content.setReason(reason);
                content.setOperatorId(operatorId);
                try {
                    sanctionMapper.insert(content);
                } catch (DuplicateKeyException ignored) {
                    // 并发同源处置：首条已代表本次动作
                }
            }
            case "SOCIAL_MUTE" -> {
                sanction.setScope(SCOPE_SOCIAL_MUTE);
                if (durationSeconds > 0) {
                    sanction.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(durationSeconds));
                }
                try {
                    sanctionMapper.insert(sanction);
                } catch (DuplicateKeyException e) {
                    return existingOrThrow(report.getId(), action);
                }
            }
            default -> throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "动作不支持处罚事实生成: " + action);
        }
        log.info("处罚事实生成: userId={}, scope={}, action={}, expiresAt={}, sourceReport={}",
                targetUserId, sanction.getScope(), action, sanction.getExpiresAt(), report.getId());
        return sanction;
    }

    private PetUserSanction existingOrThrow(Long sourceReportId, String action) {
        PetUserSanction existing = sanctionMapper.selectOne(new LambdaQueryWrapper<PetUserSanction>()
                .eq(PetUserSanction::getSourceReportId, sourceReportId)
                .eq(PetUserSanction::getAction, action)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "处罚记录写入冲突，请重试");
    }

    /** 处罚列表（管理端按 userId/status/scope 筛选） */
    public List<PetUserSanction> list(Long userId, String status, String scope, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<PetUserSanction> wrapper = new LambdaQueryWrapper<PetUserSanction>()
                .orderByDesc(PetUserSanction::getId)
                .last("LIMIT " + safeSize + " OFFSET " + (long) (safePage - 1) * safeSize);
        if (userId != null) {
            wrapper.eq(PetUserSanction::getUserId, userId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PetUserSanction::getStatus, status.toUpperCase());
        }
        if (scope != null && !scope.isBlank()) {
            wrapper.eq(PetUserSanction::getScope, scope.toUpperCase());
        }
        return sanctionMapper.selectList(wrapper);
    }

    /**
     * 撤销处罚（R05：独立审计——理由必填，撤销保留历史不物理删除）。
     */
    @Transactional
    public void revoke(Long sanctionId, Long operatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "撤销理由必填");
        }
        int updated = sanctionMapper.update(null, new LambdaUpdateWrapper<PetUserSanction>()
                .set(PetUserSanction::getStatus, "REVOKED")
                .set(PetUserSanction::getRevokedBy, operatorId)
                .set(PetUserSanction::getRevokedReason, reason.strip())
                .eq(PetUserSanction::getId, sanctionId)
                .eq(PetUserSanction::getStatus, "ACTIVE"));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "处罚不存在或已解除");
        }
        log.info("处罚已撤销: sanctionId={}, operator={}, reason={}", sanctionId, operatorId, reason);
    }
}
