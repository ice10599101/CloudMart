package com.cloudmart.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 活动参与者/报名申请（管理端，ADMIN-05）。
 *
 * <p>字段与 mall-wish 管理端 {@code listParticipantsForAdmin} 返回一致；
 * 身份字段（userId/wishId）由统一 Long 序列化保证字符串下发（BASE-01）。</p>
 */
@Schema(description = "活动参与者/报名申请")
public record WishActivityParticipantVO(
        @Schema(description = "申请用户 ID")
        Long userId,

        @Schema(description = "状态：PENDING/APPROVED/JOINED/REJECTED")
        String status,

        @Schema(description = "活动内角色")
        String role,

        @Schema(description = "关联协作心愿 ID")
        Long wishId,

        @Schema(description = "技能标签")
        String skills,

        @Schema(description = "匹配分")
        Integer matchScore,

        @Schema(description = "申请时间")
        LocalDateTime appliedAt,

        @Schema(description = "审核时间")
        LocalDateTime reviewedAt) {
}
