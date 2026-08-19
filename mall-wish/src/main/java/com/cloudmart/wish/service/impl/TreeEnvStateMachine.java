package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.config.WishTreeEnvProperties;
import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeEnvSource;

import java.time.LocalDateTime;

/**
 * 生命树环境状态机（文档 2.2 气象情绪联动，纯函数便于穷举单测）。
 *
 * <p>判定规则（优先级从上到下）：</p>
 * <ol>
 *   <li>彩虹条件满足（BLESS 突增 或 mood &gt; +0.3）且当前彩虹未激活
 *       → RAINBOW，持续 15 分钟；祝福突增可打断下雨（"收到他人祝福触发
 *       彩虹"治愈叙事，文档 2.2）</li>
 *   <li>彩虹激活中（未过期）→ 保持 RAINBOW 不续期（固定时长语义）</li>
 *   <li>mood &lt; -0.6 → RAIN；首次触发记录 triggeredAt，续雨保持原
 *       triggeredAt（作为 30 分钟最短持续基准）</li>
 *   <li>下雨未满最短持续（30 分钟）→ 维持 RAIN（防抖，即使情绪已回升）</li>
 *   <li>其余 → SUNNY（从 RAINBOW 回落标记 RAINBOW_EXPIRED，从 RAIN
 *       恢复标记 MOOD_RECOVER）</li>
 * </ol>
 */
public final class TreeEnvStateMachine {

    /** 状态机输入：当前 DB 状态 + 本次扫描观测 */
    public record TransitionInput(
            TreeEnvironment current,
            TreeEnvSource currentSource,
            LocalDateTime triggeredAt,
            LocalDateTime expiresAt,
            Double mood,
            boolean blessBurst,
            LocalDateTime now) {}

    /** 状态机输出：目标状态（触发/过期时间由调用方直接持久化） */
    public record TransitionResult(
            TreeEnvironment environment,
            TreeEnvSource source,
            LocalDateTime triggeredAt,
            LocalDateTime expiresAt) {}

    private TreeEnvStateMachine() {}

    public static TransitionResult determine(TransitionInput input, WishTreeEnvProperties props) {
        LocalDateTime now = input.now();
        boolean rainbowActive = input.current() == TreeEnvironment.RAINBOW
                && input.expiresAt() != null
                && input.expiresAt().isAfter(now);
        boolean rainbowCondition = input.blessBurst()
                || (input.mood() != null && input.mood() > props.getRainbowThreshold());
        boolean rainCondition = input.mood() != null && input.mood() < props.getRainThreshold();
        boolean rainInMinDuration = input.current() == TreeEnvironment.RAIN
                && input.triggeredAt() != null
                && input.triggeredAt().plusMinutes(props.getRainMinDurationMinutes()).isAfter(now);

        // 1. 彩虹触发（未激活时才触发，过期后条件仍满足则重新计时）
        if (rainbowCondition && !rainbowActive) {
            TreeEnvSource source = input.blessBurst()
                    ? TreeEnvSource.BLESS_BURST_RAINBOW
                    : TreeEnvSource.MOOD_RAINBOW;
            return new TransitionResult(TreeEnvironment.RAINBOW, source,
                    now, now.plusMinutes(props.getRainbowDurationMinutes()));
        }
        // 2. 彩虹激活中：固定 15 分钟时长，不续期、不被低情绪打断
        if (rainbowActive) {
            return new TransitionResult(TreeEnvironment.RAINBOW, input.currentSource(),
                    input.triggeredAt(), input.expiresAt());
        }
        // 3. 负面情绪触发/续雨
        if (rainCondition) {
            if (input.current() == TreeEnvironment.RAIN) {
                // 续雨：保持首次触发时间（30 分钟最短持续基准不重置）
                return new TransitionResult(TreeEnvironment.RAIN, TreeEnvSource.MOOD_RAIN_RENEW,
                        input.triggeredAt(), null);
            }
            return new TransitionResult(TreeEnvironment.RAIN, TreeEnvSource.MOOD_RAIN, now, null);
        }
        // 4. 下雨最短持续防抖
        if (rainInMinDuration) {
            return new TransitionResult(TreeEnvironment.RAIN, input.currentSource(),
                    input.triggeredAt(), null);
        }
        // 5. 回落晴天
        if (input.current() == TreeEnvironment.SUNNY) {
            return new TransitionResult(TreeEnvironment.SUNNY, TreeEnvSource.MOOD_RECOVER,
                    input.triggeredAt(), null);
        }
        TreeEnvSource source = input.current() == TreeEnvironment.RAINBOW
                ? TreeEnvSource.RAINBOW_EXPIRED
                : TreeEnvSource.MOOD_RECOVER;
        return new TransitionResult(TreeEnvironment.SUNNY, source, now, null);
    }
}
