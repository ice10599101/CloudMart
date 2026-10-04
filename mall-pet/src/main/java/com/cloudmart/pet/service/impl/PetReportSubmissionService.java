package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.entity.PetReport;
import com.cloudmart.pet.entity.PetWallMessage;
import com.cloudmart.pet.feign.UserFeignClient;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetReportMapper;
import com.cloudmart.pet.repository.PetWallMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 用户举报创建（R05 §7.2）：目标存在性核验 + 每日配额 + 未结案同人同对象幂等。
 *
 * <p>「同用户同目标未结案返回已有举报，不重复入队」由 V61 生成列 open_key + 唯一键
 * uk_pet_report_open 数据库权威约束（PENDING 时非空、结案自动置 NULL），应用层
 * DuplicateKeyException 转幂等返回；系统自动举报（危机词，isAuto=1）不走本服务、不占配额。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetReportSubmissionService {

    private static final Set<String> ALLOWED_TARGET_TYPES = Set.of("WALL_MESSAGE", "BOTTLE_CONTENT", "NICKNAME");
    private static final int REASON_MAX_LENGTH = 200;

    private final PetReportMapper reportMapper;
    private final PetWallMessageMapper wallMessageMapper;
    private final PetBottleRecordMapper bottleRecordMapper;
    private final PetQuotaService quotaService;
    private final PetProperties properties;
    private final PetClock petClock;
    private final UserFeignClient userFeignClient;

    /** 创建结果：deduped=true 表示命中未结案幂等（返回既有举报，未新建） */
    public record ReportSummary(Long reportId, String status, boolean deduped) {
    }

    /** 我的举报（§7.2 /reports/mine）：不含处理人等内部字段，handleReason 为面向举报人的公开处置说明 */
    public record ReportMineVO(Long reportId, String targetType, Long targetId, String reason,
                               String description, String status, String handleAction, String handleReason,
                               String createdAt, String handledAt) {
    }

    /**
     * 提交举报：校验（目标存在 → 说明长度）→ 未结案幂等预检 → 每日配额 → 落库。
     * 顺序保证：校验失败与幂等命中都不消耗当日举报配额。
     */
    @Transactional
    public ReportSummary create(Long userId, String targetType, Long targetId, String reason, String description) {
        String normalizedType = targetType == null ? null : targetType.toUpperCase(Locale.ROOT);
        if (normalizedType == null || !ALLOWED_TARGET_TYPES.contains(normalizedType)
                || targetId == null || targetId <= 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "举报参数非法");
        }
        if (reason == null || reason.isBlank() || reason.strip().length() > REASON_MAX_LENGTH) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "举报理由必填且不超过 200 字");
        }
        String normalizedDescription = description == null ? null : description.strip();
        int descriptionMax = properties.getModeration().getReportDescriptionMaxLength();
        if (normalizedDescription != null
                && (normalizedDescription.isEmpty() || normalizedDescription.length() > descriptionMax)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "补充说明需为 1~" + descriptionMax + " 字符");
        }
        requireTargetExists(normalizedType, targetId);

        PetReport existing = findOpenReport(userId, normalizedType, targetId);
        if (existing != null) {
            return new ReportSummary(existing.getId(), existing.getStatus(), true);
        }
        if (!quotaService.tryConsume(userId, PetQuotaService.QuotaType.REPORT, 0L,
                properties.getModeration().getReportDailyLimit())) {
            throw new BusinessException(PetErrorCodes.PET_QUOTA_EXHAUSTED, "今日举报次数已达上限");
        }
        PetReport report = new PetReport();
        report.setReporterUserId(userId);
        report.setTargetType(normalizedType);
        report.setTargetId(targetId);
        report.setReason(reason.strip());
        report.setDescription(normalizedDescription);
        report.setStatus("PENDING");
        report.setIsAuto(0);
        report.setReportDate(petClock.businessDate());
        try {
            reportMapper.insert(report);
            return new ReportSummary(report.getId(), report.getStatus(), false);
        } catch (DuplicateKeyException duplicate) {
            // 并发同键：释放配额后幂等返回既有举报（与预检同一约束源 uk_pet_report_open）
            quotaService.release(userId, PetQuotaService.QuotaType.REPORT, 0L);
            PetReport concurrent = findOpenReport(userId, normalizedType, targetId);
            if (concurrent != null) {
                return new ReportSummary(concurrent.getId(), concurrent.getStatus(), true);
            }
            throw duplicate;
        }
    }

    /** 我的举报列表（§7.2）：本人状态 + 公开处置摘要 + 提交时间；不含被举报者资料与内部审核字段 */
    public List<ReportMineVO> listMine(Long userId) {
        return reportMapper.selectList(new LambdaQueryWrapper<PetReport>()
                        .eq(PetReport::getReporterUserId, userId)
                        .orderByDesc(PetReport::getId)
                        .last("LIMIT 50"))
                .stream()
                .map(report -> new ReportMineVO(report.getId(), report.getTargetType(), report.getTargetId(),
                        report.getReason(), report.getDescription(), report.getStatus(),
                        report.getHandleAction(), report.getHandleReason(),
                        report.getCreatedAt() == null ? null : report.getCreatedAt().toString(),
                        report.getHandledAt() == null ? null : report.getHandledAt().toString()))
                .toList();
    }

    private PetReport findOpenReport(Long userId, String targetType, Long targetId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<PetReport>()
                .eq(PetReport::getReporterUserId, userId)
                .eq(PetReport::getTargetType, targetType)
                .eq(PetReport::getTargetId, targetId)
                .eq(PetReport::getStatus, "PENDING")
                .last("LIMIT 1"));
    }

    /** §7.2 校验对象：留言墙/捞瓶为本地域实体必须存在；NICKNAME 目标为用户 ID，mall-user 可用时核验 */
    private void requireTargetExists(String targetType, Long targetId) {
        switch (targetType) {
            case "WALL_MESSAGE" -> {
                if (wallMessageMapper.selectById(targetId) == null) {
                    throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "举报对象不存在");
                }
            }
            case "BOTTLE_CONTENT" -> {
                if (bottleRecordMapper.selectById(targetId) == null) {
                    throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "举报对象不存在");
                }
            }
            case "NICKNAME" -> requireUserKnown(targetId);
            default -> throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "举报类型不支持");
        }
    }

    /**
     * NICKNAME 目标核验：用户客户端为展示型 Fail-Open（mall-user 不可用返回空列表），
     * 降级期间不阻断举报（配额 + 未结案唯一仍约束滥用）；明确返回名单且不含目标才拒绝。
     */
    private void requireUserKnown(Long targetUserId) {
        try {
            List<java.util.Map<String, Object>> users = userFeignClient.batchGetUsers(List.of(targetUserId)).data();
            if (users != null && !users.isEmpty() && users.stream().noneMatch(user -> {
                Object id = user.get("id");
                return id instanceof Number numberId && numberId.longValue() == targetUserId;
            })) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "举报对象不存在");
            }
        } catch (BusinessException validation) {
            throw validation;
        } catch (Exception degraded) {
            log.warn("举报目标用户核验降级放行, targetUserId={}", targetUserId);
        }
    }
}
