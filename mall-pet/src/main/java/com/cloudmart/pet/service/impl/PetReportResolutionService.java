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
    /** §13.1：moderationActions 关闭时仅允许内容移除/驳回（处罚类动作明确拒绝） */
    private final com.cloudmart.pet.config.PetProperties properties;
    private final PetEventProducer eventProducer;
    /** R05：处罚事实（USER_WARNED/USER_PET_BANNED 生成 pet_user_sanction 记录） */
    private final PetAccessPolicy accessPolicy;

    /**
     * R05 处置动作矩阵（§8.2）：按 targetType 校验动作组合——
     * 暂未实现的组合返回明确不支持，禁止成功空操作。
     */
    private static final java.util.Map<String, java.util.Set<String>> ACTIONS_BY_TARGET_TYPE = java.util.Map.of(
            "WALL_MESSAGE", java.util.Set.of("CONTENT_REMOVED", "USER_WARNED", "USER_PET_BANNED", "SOCIAL_MUTE", "DISMISSED"),
            "BOTTLE_CONTENT", java.util.Set.of("CONTENT_REMOVED", "USER_WARNED", "USER_PET_BANNED", "SOCIAL_MUTE", "DISMISSED"),
            "NICKNAME", java.util.Set.of("USER_WARNED", "USER_PET_BANNED", "DISMISSED"),
            "CHAT_MESSAGE", java.util.Set.of("DISMISSED"));

    /** @return 目标用户 ID 解析（处罚归属）：NICKNAME targetId 即用户；WALL_MESSAGE 查留言行；其余不可解析返回 null */
    private Long resolveTargetUserId(PetReport report) {
        if ("NICKNAME".equals(report.getTargetType())) {
            return report.getTargetId();
        }
        if ("WALL_MESSAGE".equals(report.getTargetType())) {
            PetWallMessage message = wallMessageMapper.selectById(report.getTargetId());
            return message != null ? message.getUserId() : null;
        }
        // BOTTLE_CONTENT：瓶子属 mall-wish 域，宠物域无法从 bottleId 定位主人——
        // 该组合下内容类动作仍生效（无内容实体联动），处罚留待运营按证据补录
        return null;
    }

    /**
     * 闭环处理举报。
     *
     * @param reportId    举报 ID（须为 PENDING 状态）
     * @param action      处理动作：CONTENT_REMOVED/USER_WARNED/USER_PET_BANNED/DISMISSED
     * @param reason      处理说明（必填，通知举报人的依据）
     * @param adminUserId 处理管理员（mall-admin 代理透传的认证主体）
     */
    @Transactional
    public void resolve(Long reportId, String action, String reason, Long adminUserId,
                        Integer durationSeconds) {
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
        // R05 处置矩阵：target+action 组合不支持 → 明确拒绝（禁止成功空操作）
        if (!properties.getFeatureSwitches().isModerationActions()
                && !Set.of("CONTENT_REMOVED", "DISMISSED").contains(action)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "处罚类动作暂未开放");
        }
        java.util.Set<String> allowed = ACTIONS_BY_TARGET_TYPE.getOrDefault(
                report.getTargetType(), java.util.Set.of());
        if (!allowed.contains(normalizedAction)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "处理动作 " + normalizedAction + " 不适用于举报类型 " + report.getTargetType());
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

        // R05：处罚事实生成——USER_WARNED/USER_PET_BANNED/SOCIAL_MUTE 产生
        // pet_user_sanction 记录（同举报同动作幂等）；目标用户不可解析时留日志，
        // 由运营按证据补录（不静默宣称已处罚）
        Long targetUserId = resolveTargetUserId(report);
        if (targetUserId != null && switch (normalizedAction) {
            case "USER_WARNED", "USER_PET_BANNED", "SOCIAL_MUTE" -> true;
            default -> false;
        }) {
            accessPolicy.issueSanction(report, normalizedAction, targetUserId,
                    durationSeconds != null ? durationSeconds : 0, reason.strip(), adminUserId);
            // R05：WARN 通知被处置用户（与举报人通知同链路，事件键区分）
            eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_REPORT_RESOLVED,
                    new PetEventProducer.PetEventMessage(
                            "PET_REPORT_RESOLVED:TARGET:" + reportId,
                            String.valueOf(targetUserId),
                            "PET_REPORT_RESOLVED",
                            "你的行为违反了社区规范",
                            "处理说明：" + reason.strip() + "。如有疑问请联系客服申诉。",
                            String.valueOf(reportId), "PET_REPORT_RESOLVED"));
        } else if (targetUserId == null && switch (normalizedAction) {
            case "USER_WARNED", "USER_PET_BANNED", "SOCIAL_MUTE" -> true;
            default -> false;
        }) {
            log.warn("举报目标用户不可解析，处罚未生成（需运营补录）: reportId={}, type={}, targetId={}",
                    reportId, report.getTargetType(), report.getTargetId());
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
