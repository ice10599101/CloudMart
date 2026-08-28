package com.cloudmart.wish.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 擦肩而过匹配纯函数测试（Sprint 3.3 验收：相同/不同 geohash6、不同标签、
 * 相邻 timeBucket、位置伪造检测各种速度场景、k≥5 匿名阈值）。
 */
@DisplayName("擦肩而过匹配纯函数")
class EncounterMatcherTest {

    @Test
    @DisplayName("时间桶：向下取整到 30 分钟（验收口径）")
    void bucketFloor() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 29, 14, 47, 23);
        LocalDateTime bucket = EncounterMatcher.bucketOf(time);
        assertThat(bucket).isEqualTo(LocalDateTime.of(2026, 8, 29, 14, 30));
        // 整点边界
        assertThat(EncounterMatcher.bucketOf(LocalDateTime.of(2026, 8, 29, 15, 0, 0)))
                .isEqualTo(LocalDateTime.of(2026, 8, 29, 15, 0));
    }

    @Test
    @DisplayName("相邻桶：相同/相差 30 分钟相邻；相差 60 分钟不相邻（验收项）")
    void adjacentBuckets() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 29, 14, 30);
        assertThat(EncounterMatcher.isAdjacentBucket(base, base)).isTrue();
        assertThat(EncounterMatcher.isAdjacentBucket(base, base.plusMinutes(30))).isTrue();
        assertThat(EncounterMatcher.isAdjacentBucket(base, base.minusMinutes(30))).isTrue();
        assertThat(EncounterMatcher.isAdjacentBucket(base, base.plusMinutes(60))).isFalse();
    }

    @Test
    @DisplayName("标签匹配：交集非空配对；不同标签不配对（验收项）")
    void tagsIntersect() {
        String a = "[\"看极光\",\"旅行\"]";
        String b = "[\"看极光\",\"摄影\"]";
        String c = "[\"减肥\"]";
        assertThat(EncounterMatcher.tagsIntersect(a, b)).isTrue();
        assertThat(EncounterMatcher.tagsIntersect(a, c)).isFalse();
        assertThat(EncounterMatcher.intersectTags(a, b)).containsExactly("看极光");
    }

    @Test
    @DisplayName("匿名人群阈值：k<5 不生成信笺（文档 39.9 新增隐私契约）")
    void anonCrowdThreshold() {
        assertThat(EncounterMatcher.meetsAnonCrowdThreshold(4)).isFalse();
        assertThat(EncounterMatcher.meetsAnonCrowdThreshold(5)).isTrue();
    }

    @Test
    @DisplayName("伪造判定：步行跳跃 1 分钟 2km（120km/h > 15）→ 标记可疑")
    void spoofingWalkJump() {
        Integer verdict = EncounterMatcher.spoofingVerdict(2000.0, 1, "ws1e2z3", "ws1e6zz", List.of());
        assertThat(verdict).isNotNull();
        assertThat(verdict).isGreaterThan(15);
    }

    @Test
    @DisplayName("伪造判定：5 分钟 500m（6km/h）→ 正常不标记")
    void spoofingNormalWalk() {
        assertThat(EncounterMatcher.spoofingVerdict(500.0, 5, "ws1e2z3", "ws1e2ab", List.of()))
                .isNull();
    }

    @Test
    @DisplayName("交通枢纽放宽：起点在枢纽 geohash4 网格 → 高速不标记（验收项）")
    void spoofingHubRelaxed() {
        String from = "ws1e2z3";
        String to = "xxxxxxx";
        Integer verdict = EncounterMatcher.spoofingVerdict(200000.0, 60, from, to, List.of("ws1e"));
        assertThat(verdict).as("枢纽网格内 1 小时 200km 不标记").isNull();
        // 非枢纽同样跳跃 → 标记
        Integer marked = EncounterMatcher.spoofingVerdict(200000.0, 60, from, to, List.of("zzzz"));
        assertThat(marked).isNotNull();
    }

    @Test
    @DisplayName("标签 JSON：空/非法解析 Fail-Open 空集")
    void parseTagsFailOpen() {
        assertThat(EncounterMatcher.parseTags(null)).isEmpty();
        assertThat(EncounterMatcher.parseTags("not-json")).isEmpty();
        assertThat(EncounterMatcher.parseTags("[\"看极光\"]")).containsExactly("看极光");
    }
}
