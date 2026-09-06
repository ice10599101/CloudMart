package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.vo.AiResourceVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 树洞 AI 回复解析组件。
 *
 * <p>大模型按 Prompt 契约返回 JSON：
 * {@code {"reply": "...", "sentimentScore": -1.0~1.0, "resources": [...]}}。
 * 解析容错策略（文档 30.1 兜底原则）：</p>
 * <ul>
 *   <li>剥离 Markdown 代码围栏（qwen 偸好 ```json 包裹）后解析首个 JSON 对象</li>
 *   <li>JSON 解析失败 → 整段原文作为 reply（纯文本降级），sentiment/resources 置空</li>
 *   <li>sentimentScore 越界（&lt;-1 或 &gt;1）→ 钳制到合法区间</li>
 * </ul>
 */
@Component
@Slf4j
public class TreeHoleReplyParser {

    /** 解析结果：reply 必有；sentimentScore/resources 在降级时为 null/空 */
    public record ParsedReply(String reply, Double sentimentScore, List<AiResourceVO> resources) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析模型原始输出。
     *
     * @param rawContent 模型完整输出文本
     * @return 解析结果（永不返回 null；解析失败时 reply=原文）
     */
    public ParsedReply parse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new ParsedReply("", null, List.of());
        }
        String jsonCandidate = extractJsonCandidate(rawContent.trim());
        if (jsonCandidate == null) {
            log.debug("树洞回复非JSON格式，按纯文本降级处理, length={}", rawContent.length());
            return new ParsedReply(rawContent.trim(), null, List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(jsonCandidate);
            String reply = root.path("reply").asText("").trim();
            Double sentimentScore = readSentimentScore(root.path("sentimentScore"));
            List<AiResourceVO> resources = readResources(root.path("resources"));
            if (reply.isEmpty()) {
                // reply 缺失视为解析失败，整段原文降级，避免给用户空回复
                log.warn("树洞回复JSON缺少reply字段，按纯文本降级");
                return new ParsedReply(rawContent.trim(), null, List.of());
            }
            return new ParsedReply(reply, sentimentScore, resources);
        } catch (Exception ex) {
            log.warn("树洞回复JSON解析失败，按纯文本降级: {}", ex.getMessage());
            return new ParsedReply(rawContent.trim(), null, List.of());
        }
    }

    /**
     * 提取 JSON 候选串：剥离代码围栏后截取首个 '{' 到最后一个 '}'。
     *
     * @return JSON 候选串；不含 JSON 结构时返回 null
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

    private Double readSentimentScore(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        double value = node.asDouble();
        if (value < -1.0) {
            return -1.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private List<AiResourceVO> readResources(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<AiResourceVO> resources = new ArrayList<>();
        for (JsonNode item : node) {
            String type = item.path("type").asText(null);
            String title = item.path("title").asText(null);
            String url = item.path("url").asText(null);
            if (type != null && title != null && url != null) {
                resources.add(new AiResourceVO(type, title, url));
            }
        }
        return List.copyOf(resources);
    }
}
