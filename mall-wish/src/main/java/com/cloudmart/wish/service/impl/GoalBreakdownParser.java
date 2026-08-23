package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.config.WishAiProperties;
import com.cloudmart.wish.vo.AiBreakdownGoalVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 目标拆解输出解析组件（Sprint 2.5）。
 *
 * <p>DashScope 按 Prompt 契约返回 JSON：
 * {@code {"intent": "...", "goals": [...], "suggestion": "..."}}。
 * 解析容错策略（对齐 TreeHoleReplyParser）：</p>
 * <ul>
 *   <li>剥离 Markdown 代码围栏后截取首个 '{' 到最后一个 '}'</li>
 *   <li>goals 数量钳制到 [goalMinCount, goalMaxCount]（不足下限不报错，
 *       保留模型输出；超上限截断）</li>
 *   <li>estimatedDays 钳制 1-365，priority 钳制 1-5</li>
 *   <li>title/description 缺失的条目剔除</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoalBreakdownParser {

    /** 解析结果：intent 必有；goals 至少一条（全非法时返回空列表由调用方降级） */
    public record ParsedBreakdown(String intent, List<AiBreakdownGoalVO> goals, String suggestion) {
    }

    private static final int MAX_ESTIMATED_DAYS = 365;
    private static final int MAX_PRIORITY = 5;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WishAiProperties aiProperties;

    /**
     * 解析模型原始输出。
     *
     * @param rawContent 模型完整输出文本
     * @return 解析结果（永不返回 null；解析失败时 intent=原文截断、goals=空列表）
     */
    public ParsedBreakdown parse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new ParsedBreakdown("", List.of(), "");
        }
        String jsonCandidate = extractJsonCandidate(rawContent.trim());
        if (jsonCandidate == null) {
            log.warn("拆解输出非JSON格式, length={}", rawContent.length());
            return degradeToText(rawContent.trim());
        }
        try {
            JsonNode root = objectMapper.readTree(jsonCandidate);
            String intent = root.path("intent").asText("").trim();
            String suggestion = root.path("suggestion").asText("").trim();
            List<AiBreakdownGoalVO> goals = readGoals(root.path("goals"));
            if (goals.isEmpty()) {
                log.warn("拆解输出JSON缺少有效goals，文本降级");
                return degradeToText(rawContent.trim());
            }
            return new ParsedBreakdown(intent, goals, suggestion);
        } catch (Exception ex) {
            log.warn("拆解输出JSON解析失败，文本降级: {}", ex.getMessage());
            return degradeToText(rawContent.trim());
        }
    }

    private List<AiBreakdownGoalVO> readGoals(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<AiBreakdownGoalVO> goals = new ArrayList<>();
        for (JsonNode item : node) {
            String title = item.path("title").asText("").trim();
            String description = item.path("description").asText("").trim();
            if (title.isEmpty() || description.isEmpty()) {
                continue;
            }
            goals.add(new AiBreakdownGoalVO(title, description,
                    clampEstimatedDays(item.path("estimatedDays")),
                    clampPriority(item.path("priority"))));
        }
        // 超上限截断（保留前 N 个：Prompt 要求从易到难排列）
        if (goals.size() > aiProperties.getGoalMaxCount()) {
            goals = new ArrayList<>(goals.subList(0, aiProperties.getGoalMaxCount()));
        }
        return List.copyOf(goals);
    }

    private Integer clampEstimatedDays(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return 7;
        }
        return Math.clamp(node.asInt(), 1, MAX_ESTIMATED_DAYS);
    }

    private Integer clampPriority(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return 3;
        }
        return Math.clamp(node.asInt(), 1, MAX_PRIORITY);
    }

    /**
     * 纯文本降级：intent 取原文前 20 字，goals 置空——调用方据此判定拆解失败
     * 并抛 WISH_AI_UNAVAILABLE（避免把不可执行的"步骤"给用户）。
     */
    private ParsedBreakdown degradeToText(String content) {
        String intent = content.length() <= 20 ? content : content.substring(0, 20);
        return new ParsedBreakdown(intent, List.of(), "");
    }

    /**
     * 提取 JSON 候选串：剥离代码围栏后截取首个 '{' 到最后一个 '}'。
     */
    private String extractJsonCandidate(String content) {
        String stripped = content;
        if (stripped.startsWith("```")) {
            int firstLineBreak = stripped.indexOf('\n');
            if (firstLineBreak > 0 && stripped.endsWith("```")) {
                stripped = stripped.substring(firstLineBreak + 1, stripped.length() - 3).trim();
            }
        }
        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return stripped.substring(start, end + 1);
    }
}
