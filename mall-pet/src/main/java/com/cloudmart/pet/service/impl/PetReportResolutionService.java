package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.entity.PetReport;
import com.cloudmart.pet.entity.PetWallMessage;
import com.cloudmart.pet.enums.PetWallStatus;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetReportMapper;
import com.cloudmart.pet.repository.PetWallMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;

/**
 * 举报处理闭环（P0-2）：处理动作 + 处理说明 + 举报人通知 + 内容联动下架。
 *
 * <p>状态机：PENDING → HANDLED（实体处置）/ REJECTED（DISMISSED 驳回），
 * 处理结果经 outbox 发 {@code PET_REPORT_RESOLVED} 事件（事务提交后投递，
 * mall-notification 落站内信推送举报人）；action=CONTENT_REMOVED 且举报对象为
 * 留言墙留言时，联动将留言置 HIDDEN（对用户不可见，管理端保留溯源）。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetReportResolutionService {

    /** 处理动作 → 举报人通知文案（reason 会附加在文案后） */
    private static final Set<String> RESOLUTION_ACTIONS =
            Set.of("CONTENT_REMOVED", "USER_WARNED", "USER_PET_BANNED", "DISMISSED");

    private final PetReportMapper reportMapper;
    private final PetWallMessageMapper wallMessageMapper;
    private final PetEventProducer eventProducer;

    /**
     * 闭环处理举报。
     *
     * @param reportId    举报 ID（须为 PENDING 状态）
     * @param action      处理动作：CONTENT_REMOVED/USER_WARNED/USER_PET_BANNED/DISMISSED
     * @param reason      处理说明（必填，通知举报人的依据）
     * @param adminUserId 处理管理员（mall-admin 代理透传的认证主体）
     */
    @Transactional
    public void resolve(Long reportId, String action, String reason, Long adminUserId) {
        String normalizedAction = action != null ? action.strip().toUpperCase(Locale.ROOT) : "";
        if (!RESOLUTION_ACTIONS.contains(normalizedAction)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "处理动作必须是 " + RESOLUTION_ACTIONS);
        }
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "处理说明必填且不超过 200 字");
        }

        PetReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "举报不存在");
        }
        String status = "DISMISSED".equals(normalizedAction) ? "REJECTED" : "HANDLED";
        int updated = reportMapper.update(null, new LambdaUpdateWrapper<PetReport>()
                .set(PetReport::getStatus, status)
                .set(PetReport::getHandledBy, adminUserId)
                .set(PetReport::getHandledAt, LocalDateTime.now(ZoneOffset.UTC))
                .set(PetReport::getHandleAction, normalizedAction)
                .set(PetReport::getHandleReason, reason.strip())
                .eq(PetReport::getId, reportId)
                .eq(PetReport::getStatus, "PENDING"));
        if (updated == 0) {
            // 并发处理：仅首个提交生效
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "举报不存在或已处理");
        }

        if ("CONTENT_REMOVED".equals(normalizedAction)
                && "WALL_MESSAGE".equals(report.getTargetType())) {
            hideWallMessage(report.getTargetId());
        }

        // 举报人通知：outbox 事务后投递（事件键幂等，重复处理不重发）
        eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_REPORT_RESOLVED,
                new PetEventProducer.PetEventMessage(
                        "PET_REPORT_RESOLVED:" + reportId,
                        String.valueOf(report.getReporterUserId()),
                        "PET_REPORT_RESOLVED",
                        "你的举报已处理",
                        resolutionText(normalizedAction, reason.strip()),
                        String.valueOf(reportId), "PET_REPORT_RESOLVED"));
        log.info("举报已闭环处理: reportId={}, action={}, status={}, adminUserId={}",
                reportId, normalizedAction, status, adminUserId);
    }

    /** 联动下架：仅 NORMAL → HIDDEN（已删除/已隐藏内容视为已不可见，跳过） */
    private void hideWallMessage(Long messageId) {
        int updated = wallMessageMapper.update(null, new LambdaUpdateWrapper<PetWallMessage>()
                .set(PetWallMessage::getStatus, PetWallStatus.HIDDEN.name())
                .eq(PetWallMessage::getId, messageId)
                .eq(PetWallMessage::getStatus, PetWallStatus.NORMAL.name()));
        if (updated == 0) {
            log.info("举报联动下架跳过（内容已不可见）: messageId={}", messageId);
        }
    }

    private String resolutionText(String action, String reason) {
        String outcome = switch (action) {
            case "CONTENT_REMOVED" -> "你举报的内容已下架处理";
            case "USER_WARNED" -> "你举报的对象已被警告";
            case "USER_PET_BANNED" -> "你举报的对象已被封禁";
            default -> "经核实，你举报的内容未违反社区规范";
        };
        return outcome + "。处理说明：" + reason + "。感谢你维护社区环境！";
    }
}
