package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.enums.BadgeConditionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 徽章 condition JSON 解析/校验器（纯函数，便于穷举单测）。
 *
 * <p>Schema（文档 2.9 / 管理端 condition JSON 编辑校验复用本类）：</p>
 * <pre>{@code
 * {
 *   "type": "WISH_CREATED | WISH_FULFILLED | TOTAL_HELPED | TOTAL_CHECKIN_DAYS",  // 必填
 *   "threshold": 1,       // 必填，正整数（metric >= threshold 判定达标）
 *   "description": "发布第一个心愿"  // 必填，前端展示获取方式
 * }
 * }</pre>
 *
 * <p>解析失败不抛异常（Fail-Open）：该徽章跳过判定，避免单个脏配置
 * 阻断整个统计变更事务。</p>
 */
public final class BadgeConditionParser {

    /** 解析结果：null 字段由 {@link #parse} 保证非空 */
    public record BadgeCondition(BadgeConditionType type, int threshold, String description) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BadgeConditionParser() {}

    /**
     * 解析 condition JSON。
     *
     * @param conditionJson wish_badge.condition 列内容（可能为 null/非法 JSON/缺字段）
     * @return 解析结果；null 表示不可解析（调用方跳过该徽章）
     */
    public static BadgeCondition parse(String conditionJson) {
        if (conditionJson == null || conditionJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(conditionJson);
            return extract(node);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 校验管理端提交的 condition JSON（编辑器保存前校验）。
     *
     * @return null 表示合法；否则为可读错误信息（不含敏感数据）
     */
    public static String validate(String conditionJson) {
        if (conditionJson == null || conditionJson.isBlank()) {
            return "condition 不能为空";
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(conditionJson);
        } catch (JsonProcessingException e) {
            return "condition 不是合法 JSON: " + e.getOriginalMessage();
        }
        JsonNode typeNode = node.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            return "type 必填且为字符串";
        }
        try {
            BadgeConditionType.valueOf(typeNode.asText());
        } catch (IllegalArgumentException e) {
            return "type 必须为: WISH_CREATED/WISH_FULFILLED/TOTAL_HELPED/TOTAL_CHECKIN_DAYS 之一";
        }
        JsonNode thresholdNode = node.get("threshold");
        if (thresholdNode == null || !thresholdNode.canConvertToInt() || thresholdNode.asInt() <= 0) {
            return "threshold 必填且为正整数";
        }
        JsonNode descNode = node.get("description");
        if (descNode == null || descNode.asText().isBlank()) {
            return "description 必填且非空白";
        }
        return null;
    }

    private static BadgeCondition extract(JsonNode node) {
        JsonNode typeNode = node.get("type");
        JsonNode thresholdNode = node.get("threshold");
        JsonNode descNode = node.get("description");
        if (typeNode == null || thresholdNode == null || descNode == null) {
            return null;
        }
        BadgeConditionType type;
        try {
            type = BadgeConditionType.valueOf(typeNode.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!thresholdNode.canConvertToInt() || thresholdNode.asInt() <= 0) {
            return null;
        }
        String description = descNode.asText();
        if (description.isBlank()) {
            return null;
        }
        return new BadgeCondition(type, thresholdNode.asInt(), description);
    }
}
