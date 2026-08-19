package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.config.WishTreeEnvProperties;
import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeEnvSource;
import com.cloudmart.wish.service.impl.TreeEnvStateMachine.TransitionInput;
import com.cloudmart.wish.service.impl.TreeEnvStateMachine.TransitionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TreeEnvStateMachine 单元测试（文档 2.2 阈值与持续语义穷举）。
 *
 * <p>默认参数：rain &lt; -0.6 / rainbow &gt; +0.3 / RAIN 最短 30 分钟 /
 * RAINBOW 15 分钟。</p>
 */
@DisplayName("TreeEnvStateMachine 单元测试")
class TreeEnvStateMachineTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 12, 0);

    private WishTreeEnvProperties props;

    @BeforeEach
    void setUp() {
        props = new WishTreeEnvProperties();
    }

    private TransitionResult determine(TreeEnvironment current, TreeEnvSource source,
                                        LocalDateTime triggeredAt, LocalDateTime expiresAt,
                                        Double mood, boolean blessBurst) {
        return TreeEnvStateMachine.determine(
                new TransitionInput(current, source, triggeredAt, expiresAt,
                        mood, blessBurst, NOW), props);
    }

    @Nested
    @DisplayName("下雨触发与持续")
    class RainTests {

        @Test
        @DisplayName("SUNNY + mood<-0.6：触发 RAIN，记录触发时间，无过期")
        void sunnyToRain() {
            TransitionResult result = determine(TreeEnvironment.SUNNY, TreeEnvSource.MOOD_RECOVER,
                    null, null, -0.65, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.RAIN);
            assertThat(result.source()).isEqualTo(TreeEnvSource.MOOD_RAIN);
            assertThat(result.triggeredAt()).isEqualTo(NOW);
            assertThat(result.expiresAt()).isNull();
        }

        @Test
        @DisplayName("阈值边界：mood 恰为 -0.6 不触发（文档定义严格小于）")
        void moodExactlyAtThreshold_noRain() {
            TransitionResult result = determine(TreeEnvironment.SUNNY, TreeEnvSource.MOOD_RECOVER,
                    null, null, -0.6, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.SUNNY);
        }

        @Test
        @DisplayName("RAIN 续雨：保持首次触发时间（最短持续基准不重置）")
        void rainRenews_keepFirstTriggeredAt() {
            LocalDateTime firstTrigger = NOW.minusMinutes(10);
            TransitionResult result = determine(TreeEnvironment.RAIN, TreeEnvSource.MOOD_RAIN,
                    firstTrigger, null, -0.8, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.RAIN);
            assertThat(result.source()).isEqualTo(TreeEnvSource.MOOD_RAIN_RENEW);
            assertThat(result.triggeredAt()).isEqualTo(firstTrigger);
        }

        @Test
        @DisplayName("防抖：RAIN 未满 30 分钟且情绪回升 → 维持 RAIN")
        void rainMinDuration_holdsRain() {
            LocalDateTime firstTrigger = NOW.minusMinutes(29);
            TransitionResult result = determine(TreeEnvironment.RAIN, TreeEnvSource.MOOD_RAIN,
                    firstTrigger, null, 0.1, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.RAIN);
            assertThat(result.triggeredAt()).isEqualTo(firstTrigger);
        }

        @Test
        @DisplayName("恢复：RAIN 满 30 分钟且情绪回升 → SUNNY（MOOD_RECOVER）")
        void rainExpired_recoversToSunny() {
            TransitionResult result = determine(TreeEnvironment.RAIN, TreeEnvSource.MOOD_RAIN,
                    NOW.minusMinutes(30), null, 0.1, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.SUNNY);
            assertThat(result.source()).isEqualTo(TreeEnvSource.MOOD_RECOVER);
        }
    }

    @Nested
    @DisplayName("彩虹触发与持续")
    class RainbowTests {

        @Test
        @DisplayName("SUNNY + mood>+0.3：触发 RAINBOW，15 分钟过期")
        void sunnyToRainbow() {
            TransitionResult result = determine(TreeEnvironment.SUNNY, TreeEnvSource.MOOD_RECOVER,
                    null, null, 0.35, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.RAINBOW);
            assertThat(result.source()).isEqualTo(TreeEnvSource.MOOD_RAINBOW);
            assertThat(result.triggeredAt()).isEqualTo(NOW);
            assertThat(result.expiresAt()).isEqualTo(NOW.plusMinutes(15));
        }

        @Test
        @DisplayName("BLESS 突增可打断下雨（情绪低落时祝福治愈叙事）")
        void blessBurst_interruptsRain() {
            LocalDateTime firstTrigger = NOW.minusMinutes(5);
            TransitionResult result = determine(TreeEnvironment.RAIN, TreeEnvSource.MOOD_RAIN,
                    firstTrigger, null, -0.8, true);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.RAINBOW);
            assertThat(result.source()).isEqualTo(TreeEnvSource.BLESS_BURST_RAINBOW);
        }

        @Test
        @DisplayName("RAINBOW 激活中：情绪再低也不提前结束，不续期")
        void activeRainbow_notInterruptedOrExtended() {
            LocalDateTime triggeredAt = NOW.minusMinutes(5);
            LocalDateTime expiresAt = NOW.plusMinutes(10);
            TransitionResult result = determine(TreeEnvironment.RAINBOW,
                    TreeEnvSource.BLESS_BURST_RAINBOW, triggeredAt, expiresAt, -0.9, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.RAINBOW);
            assertThat(result.expiresAt()).isEqualTo(expiresAt);
            assertThat(result.source()).isEqualTo(TreeEnvSource.BLESS_BURST_RAINBOW);
        }

        @Test
        @DisplayName("RAINBOW 过期 + 情绪仍低 → 回到 RAIN 重新计时")
        void rainbowExpired_moodStillLow_fallsToRain() {
            TransitionResult result = determine(TreeEnvironment.RAINBOW,
                    TreeEnvSource.MOOD_RAINBOW, NOW.minusMinutes(20),
                    NOW.minusMinutes(5), -0.8, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.RAIN);
            assertThat(result.source()).isEqualTo(TreeEnvSource.MOOD_RAIN);
            assertThat(result.triggeredAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("RAINBOW 过期 + 条件仍满足 → 重新触发彩虹刷新时间窗")
        void rainbowExpired_conditionHolds_retriggers() {
            TransitionResult result = determine(TreeEnvironment.RAINBOW,
                    TreeEnvSource.MOOD_RAINBOW, NOW.minusMinutes(20),
                    NOW.minusMinutes(5), 0.5, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.RAINBOW);
            assertThat(result.triggeredAt()).isEqualTo(NOW);
            assertThat(result.expiresAt()).isEqualTo(NOW.plusMinutes(15));
        }

        @Test
        @DisplayName("RAINBOW 过期 + 情绪中性 → 回落 SUNNY（RAINBOW_EXPIRED）")
        void rainbowExpired_neutralMood_fallsToSunny() {
            TransitionResult result = determine(TreeEnvironment.RAINBOW,
                    TreeEnvSource.MOOD_RAINBOW, NOW.minusMinutes(20),
                    NOW.minusMinutes(5), 0.0, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.SUNNY);
            assertThat(result.source()).isEqualTo(TreeEnvSource.RAINBOW_EXPIRED);
        }

        @Test
        @DisplayName("阈值边界：mood 恰为 +0.3 不触发（文档定义严格大于）")
        void moodExactlyAtRainbowThreshold_noRainbow() {
            TransitionResult result = determine(TreeEnvironment.SUNNY, TreeEnvSource.MOOD_RECOVER,
                    null, null, 0.3, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.SUNNY);
        }
    }

    @Nested
    @DisplayName("晴天维持与无数据")
    class SunnyTests {

        @Test
        @DisplayName("SUNNY + 中性情绪：维持不变")
        void sunnyStays() {
            TransitionResult result = determine(TreeEnvironment.SUNNY, TreeEnvSource.MOOD_RECOVER,
                    null, null, 0.0, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.SUNNY);
        }

        @Test
        @DisplayName("无样本（mood=null）：不触发任何环境，SUNNY 维持")
        void nullMood_noTransition() {
            TransitionResult result = determine(TreeEnvironment.SUNNY, TreeEnvSource.MOOD_RECOVER,
                    null, null, null, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.SUNNY);
        }

        @Test
        @DisplayName("无样本 + RAIN 未满最短持续：维持 RAIN（防抖独立于情绪数据）")
        void nullMood_rainMinDurationStillHolds() {
            LocalDateTime firstTrigger = NOW.minusMinutes(10);
            TransitionResult result = determine(TreeEnvironment.RAIN, TreeEnvSource.MOOD_RAIN,
                    firstTrigger, null, null, false);
            assertThat(result.environment()).isEqualTo(TreeEnvironment.RAIN);
            assertThat(result.triggeredAt()).isEqualTo(firstTrigger);
        }
    }
}
