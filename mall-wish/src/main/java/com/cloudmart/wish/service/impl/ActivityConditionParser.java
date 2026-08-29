package com.cloudmart.wish.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 活动条件解析纯函数（Sprint 3.5，文档 3.5 验收：condition JSON 触发
 * 条件正确解析）。无依赖静态方法，可独立单测。
 *
 * <p>条件模板（3 个示例）：
 * {@code {"type":"PROGRESS_COUNTER","threshold":100}} —— 活动进度计数达标；
 * {@code {"type":"PARTICIPANT_COUNT","threshold":50}} —— 参与人数达标；
 * {@code {"type":"MEMBER_FULFILLED"}} —— 合伙人组内任一成员心愿达成。</p>
 */
public final class ActivityConditionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum ConditionType {
        /** 活动进度计数达标 */
        PROGRESS_COUNTER,
        /** 参与人数达标 */
        PARTICIPANT_COUNT,
        /** 合伙人组内任一成员心愿达成 */
        MEMBER_FULFILLED
    }

    private ActivityConditionParser() {
    }

    /**
     * 校验 condition JSON（管理端保存时调用；非法抛 IllegalArgumentException，
     * message 可直接反馈编辑器）。
     */
    public static void validate(String conditionJson) {
        if (conditionJson == null || conditionJson.isBlank()) {
            return;
        }
        try {
            JsonNode node = MAPPER.readTree(conditionJson);
            String type = node.path("type").asText("");
            ConditionType.valueOf(type);
            if (node.has("threshold") && !node.get("threshold").isInt()) {
                throw new IllegalArgumentException("threshold 须为整数");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("condition JSON 非法: " + ex.getMessage());
        }
    }

    /**
     * 条件判定（纯函数）：进度/参与人数类条件与当前计数比较（≥ 阈值，
     * 无阈值视为 0 即达成）；MEMBER_FULFILLED 由调用方传入组内达成标记。
     */
    public static boolean isMet(String conditionJson, long progressCounter,
                                long participantCount, boolean anyMemberFulfilled) {
        JsonNode node = parseOrNull(conditionJson);
        if (node == null) {
            // 无条件配置：参与即达标
            return participantCount > 0;
        }
        String type = node.path("type").asText("");
        long threshold = node.path("threshold").asLong(0);
        return switch (ConditionType.valueOf(type)) {
            case PROGRESS_COUNTER -> progressCounter >= threshold;
            case PARTICIPANT_COUNT -> participantCount >= threshold;
            case MEMBER_FULFILLED -> anyMemberFulfilled;
        };
    }

    /** 招募需求技能清单（合伙人匹配度计算用；condition.skills 数组） */
    public static List<String> requiredSkills(String conditionJson) {
        JsonNode node = parseOrNull(conditionJson);
        List<String> skills = new ArrayList<>();
        if (node != null && node.has("skills") && node.get("skills").isArray()) {
            node.get("skills").forEach(s -> skills.add(s.asText()));
        }
        return skills;
    }

    /** 技能匹配度：申请技能与需求技能交集占比（0-100） */
    public static int matchScore(List<String> requiredSkills, List<String> applicantSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return 100;
        }
        if (applicantSkills == null || applicantSkills.isEmpty()) {
            return 0;
        }
        long hit = applicantSkills.stream().filter(requiredSkills::contains).distinct().count();
        return (int) Math.round(hit * 100.0 / requiredSkills.size());
    }

    private static JsonNode parseOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }
}
