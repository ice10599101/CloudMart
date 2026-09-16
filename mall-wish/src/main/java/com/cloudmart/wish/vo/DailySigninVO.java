package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 每日签到结果（文档 L852：POST /wish/my/checkin）。
 *
 * @param signed           本次签到是否成功（恒 true，重复签到抛 409）
 * @param consecutiveDays 连续签到天数（今日已含；昨日未签则重置为 1）
 * @param starlightReward  本次实际入账星光（基础 + 连续加成；余额达 5000 上限时可能截断）
 * @param tomorrowReward   明日签到可获得的星光（基础 + 连续加成）
 * @param expReward        本次应发经验（基础 + 连续加成）
 * @param expGranted       经验是否成功入账（community 不可用时 false，星光不受影响）
 * @param levelUp          签到瞬间检测到的等级提升事件（未提升为 null；三端庆祝弹窗/APP 本地推送依据）
 */
@Schema(description = "每日签到结果")
public record DailySigninVO(
        @Schema(description = "本次签到成功（恒 true）") boolean signed,
        @Schema(description = "连续签到天数（今日已含）") int consecutiveDays,
        @Schema(description = "本次实际入账星光") int starlightReward,
        @Schema(description = "明日签到可获得的星光") int tomorrowReward,
        @Schema(description = "本次应发经验") int expReward,
        @Schema(description = "经验是否成功入账") boolean expGranted,
        @Schema(description = "等级提升事件（未提升为 null）") LevelUpVO levelUp) {
}
