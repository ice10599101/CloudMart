package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 连续签到里程碑领取结果。
 *
 * @param milestoneDays   领取的里程碑天数
 * @param starlightReward 实际到账星光（余额达 5000 上限时可能截断）
 * @param expReward       本次应发经验
 * @param expGranted      经验是否成功入账（community 不可用时 false，星光不受影响）
 * @param levelUp         领取瞬间检测到的等级提升事件（未提升为 null）
 */
@Schema(description = "连续签到里程碑领取结果")
public record SigninMilestoneClaimVO(
        @Schema(description = "里程碑连续签到天数") int milestoneDays,
        @Schema(description = "实际到账星光") int starlightReward,
        @Schema(description = "本次应发经验") int expReward,
        @Schema(description = "经验是否成功入账") boolean expGranted,
        @Schema(description = "等级提升事件（未提升为 null）") LevelUpVO levelUp) {
}