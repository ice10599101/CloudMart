package com.cloudmart.wish.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 灰度路由纯函数测试（Sprint 2.8 验收：哈希分流/边界值）。
 */
@DisplayName("灰度路由纯函数")
class GrayRouterTest {

    @Test
    @DisplayName("边界：ratio=0 全部不命中；ratio=100 全部命中")
    void boundaryRatios() {
        for (long userId = 1; userId <= 300; userId++) {
            assertThat(GrayRouter.isHit(userId, "wish_ai_assistant", 0)).isFalse();
            assertThat(GrayRouter.isHit(userId, "wish_ai_assistant", 100)).isTrue();
        }
    }

    @Test
    @DisplayName("稳定性：同一用户同一功能重复判定恒一致（验收：始终命中同一灰度档）")
    void stableForSameUser() {
        for (long userId = 1; userId <= 200; userId++) {
            boolean first = GrayRouter.isHit(userId, "wish_match_squad", 50);
            for (int i = 0; i < 5; i++) {
                assertThat(GrayRouter.isHit(userId, "wish_match_squad", 50))
                        .as("userId=%d 重复判定应一致", userId)
                        .isEqualTo(first);
            }
        }
    }

    @Test
    @DisplayName("单调性：低比例命中者在高比例下必命中（5% ⊂ 20% ⊂ 50%）")
    void monotonicLadder() {
        for (long userId = 1; userId <= 500; userId++) {
            if (GrayRouter.isHit(userId, "wish_leaderboard", 5)) {
                assertThat(GrayRouter.isHit(userId, "wish_leaderboard", 20)).isTrue();
            }
            if (GrayRouter.isHit(userId, "wish_leaderboard", 20)) {
                assertThat(GrayRouter.isHit(userId, "wish_leaderboard", 50)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("分布：500 用户 50% 档命中数在 200-300 之间（哈希近似均匀）")
    void roughDistribution() {
        int hits = 0;
        for (long userId = 1; userId <= 500; userId++) {
            if (GrayRouter.isHit(userId, "wish_ai_assistant", 50)) {
                hits++;
            }
        }
        assertThat(hits).as("50% 档 500 用户命中数应接近 250").isBetween(200, 300);
    }

    @Test
    @DisplayName("桶位：0-99 全值域且确定性可复现")
    void bucketRange() {
        Set<Integer> buckets = new HashSet<>();
        for (long userId = 1; userId <= 1000; userId++) {
            int bucket = GrayRouter.bucket(userId, "wish_tree_hole");
            assertThat(bucket).isBetween(0, 99);
            buckets.add(bucket);
            assertThat(bucket).isEqualTo(GrayRouter.bucket(userId, "wish_tree_hole"));
        }
        assertThat(buckets.size()).as("1000 用户应覆盖大部分桶位").isGreaterThan(80);
    }

    @Test
    @DisplayName("匿名：仅 ratio>=100 放行（降级方向明确）")
    void anonymousOnlyFullRollout() {
        assertThat(GrayRouter.isHit(null, "wish_ai_assistant", 0)).isFalse();
        assertThat(GrayRouter.isHit(null, "wish_ai_assistant", 50)).isFalse();
        assertThat(GrayRouter.isHit(null, "wish_ai_assistant", 100)).isTrue();
    }
}
