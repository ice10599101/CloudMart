package com.cloudmart.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 邀请信息（N-3）。
 */
@Schema(description = "邀请信息")
public record InviteInfoVO(
        @Schema(description = "我的邀请码") String code,
        @Schema(description = "已成功邀请人数") int invitedCount,
        @Schema(description = "每成功邀请一人双方各得星光") int rewardPerInvite,
        @Schema(description = "WISH-04：邀请规则版本") int ruleVersion,
        @Schema(description = "WISH-04：奖励状态（PENDING_ELIGIBILITY/ELIGIBLE/GRANTED/HOLD/REJECTED_LIMIT）")
        String rewardStatus,
        @Schema(description = "WISH-04：进度/限制说明") String rewardReason
) {
}
