package com.cloudmart.wish.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 擦肩而过匹配纯函数（Sprint 3.3，文档 3.3/39.9）。
 *
 * <p>匹配三条件：相同 geohash6（key 结构天然保证）+ 心愿标签交集非空 +
 * timeBucket 相同或相邻（相邻 = 桶差 ≤ 1 桶，桶粒度 30 分钟）。
 * 最小匿名人群阈值（文档 39.9 新增隐私契约）：同桶同格用户数 &lt; 5
 * 不得生成信笺（防小样本身份反推）。</p>
 */
public final class EncounterMatcher {

    /** 时间桶粒度（分钟，文档 39.x：30 分钟时间桶向下取整） */
    public static final int BUCKET_MINUTES = 30;

    /** 最小匿名人群阈值（文档 39.9：k >= 5） */
    public static final int MIN_ANON_CROWD = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TAG_LIST = new TypeReference<>() {
    };

    private EncounterMatcher() {
    }

    /** 30 分钟时间桶（向下取整，UTC） */
    public static LocalDateTime bucketOf(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.HOURS)
                .plusMinutes((time.getMinute() / BUCKET_MINUTES) * (long) BUCKET_MINUTES);
    }

    /** 相邻桶判定：桶差绝对值 ≤ 1 桶（相同或相邻） */
    public static boolean isAdjacentBucket(LocalDateTime a, LocalDateTime b) {
        return Math.abs(ChronoUnit.MINUTES.between(a, b)) <= BUCKET_MINUTES;
    }

    /** 心愿标签交集非空（JSON 数字符串解析；解析失败按空集处理 Fail-Open） */
    public static boolean tagsIntersect(String tagsJsonA, String tagsJsonB) {
        Set<String> a = parseTags(tagsJsonA);
        Set<String> b = parseTags(tagsJsonB);
        for (String tag : a) {
            if (b.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** 双方重叠标签（信笺诗意文案取用） */
    public static List<String> intersectTags(String tagsJsonA, String tagsJsonB) {
        Set<String> a = parseTags(tagsJsonA);
        Set<String> b = parseTags(tagsJsonB);
        Set<String> overlap = new LinkedHashSet<>();
        for (String tag : a) {
            if (b.contains(tag)) {
                overlap.add(tag);
            }
        }
        return new ArrayList<>(overlap);
    }

    /**
     * 匿名人群阈值判定：同桶同格用户数 ≥ 5 才允许生成信笺
     * （文档 39.9：小样本场景下用户身份可能被反推）。
     */
    public static boolean meetsAnonCrowdThreshold(int userCount) {
        return userCount >= MIN_ANON_CROWD;
    }

    /**
     * 位置伪造判定（文档 3.3）：速度 km/h > 15 → 可疑；
     * 交通枢纽放宽：起点或终点 geohash4 ∈ 枢纽清单 → 不标记。
     *
     * @return 推断速度 km/h（供可疑记录）；hub 放宽时返回 null（不标记）
     */
    public static Integer spoofingVerdict(double distanceMeters, long durationMinutes,
                                          String fromGeohash7, String toGeohash7,
                                          java.util.List<String> hubGeohash4List) {
        if (durationMinutes <= 0) {
            // 时间未推进无法判定速度，按可疑口径记录（0 分钟移动即异常）
            return null;
        }
        double speedKmh = distanceMeters / 1000.0 / (durationMinutes / 60.0);
        if (speedKmh <= 15.0) {
            return null;
        }
        // 交通枢纽放宽：起点/终点落在枢纽 geohash4 网格 → 不标记
        if (hubGeohash4List != null) {
            for (String hub : hubGeohash4List) {
                if (hub != null && !hub.isBlank()
                        && (fromGeohash7.startsWith(hub) || toGeohash7.startsWith(hub))) {
                    return null;
                }
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(speedKmh));
    }

    /** JSON 标签数组解析（单测/服务共用；异常 Fail-Open 空集） */
    public static Set<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Set.of();
        }
        try {
            return new HashSet<>(MAPPER.readValue(tagsJson, TAG_LIST));
        } catch (Exception ex) {
            return Set.of();
        }
    }
}
