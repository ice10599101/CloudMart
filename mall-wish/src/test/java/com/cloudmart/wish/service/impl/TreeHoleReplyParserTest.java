package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.service.impl.TreeHoleReplyParser.ParsedReply;
import com.cloudmart.wish.vo.AiResourceVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TreeHoleReplyParser 单元测试（AI 输出容错解析）。
 */
@DisplayName("TreeHoleReplyParser 单元测试")
class TreeHoleReplyParserTest {

    private final TreeHoleReplyParser parser = new TreeHoleReplyParser();

    @Test
    @DisplayName("标准 JSON：完整解析 reply/sentimentScore/resources")
    void shouldParseStandardJson() {
        String raw = """
                {"reply": "我听到了你的心情，慢慢来", "sentimentScore": -0.6,
                 "resources": [{"type": "HOTLINE", "title": "热线", "url": "tel:12356"}]}""";

        ParsedReply reply = parser.parse(raw);

        assertThat(reply.reply()).isEqualTo("我听到了你的心情，慢慢来");
        assertThat(reply.sentimentScore()).isEqualTo(-0.6);
        assertThat(reply.resources()).containsExactly(
                new AiResourceVO("HOTLINE", "热线", "tel:12356"));
    }

    @Test
    @DisplayName("Markdown 代码围栏包裹：剥离后解析")
    void shouldParseMarkdownFencedJson() {
        String raw = """
                ```json
                {"reply": "围栏内容", "sentimentScore": 0.2, "resources": []}
                ```""";

        ParsedReply reply = parser.parse(raw);

        assertThat(reply.reply()).isEqualTo("围栏内容");
        assertThat(reply.sentimentScore()).isEqualTo(0.2);
        assertThat(reply.resources()).isEmpty();
    }

    @Test
    @DisplayName("JSON 前后混有多余文本：截取首 { 到末 } 解析")
    void shouldParseJsonWithSurroundingText() {
        String raw = "好的，这是我的回复：{\"reply\": \"正文\", \"sentimentScore\": 0, \"resources\": []} 希望对你有帮助";

        ParsedReply reply = parser.parse(raw);

        assertThat(reply.reply()).isEqualTo("正文");
        assertThat(reply.sentimentScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("sentimentScore 越界：钳制到 [-1.0, 1.0]")
    void shouldClampSentimentScore() {
        ParsedReply tooNegative = parser.parse(
                "{\"reply\": \"a\", \"sentimentScore\": -2.5, \"resources\": []}");
        ParsedReply tooPositive = parser.parse(
                "{\"reply\": \"a\", \"sentimentScore\": 3.0, \"resources\": []}");

        assertThat(tooNegative.sentimentScore()).isEqualTo(-1.0);
        assertThat(tooPositive.sentimentScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("resources 字段缺失或元素不完整：忽略无效元素")
    void shouldIgnoreInvalidResourceItems() {
        ParsedReply reply = parser.parse("""
                {"reply": "a", "sentimentScore": 0.1,
                 "resources": [{"type": "ARTICLE", "title": "缺url"}, {"type": "HOTLINE", "title": "全", "url": "tel:1"}]}""");

        assertThat(reply.resources()).containsExactly(
                new AiResourceVO("HOTLINE", "全", "tel:1"));
    }

    @Test
    @DisplayName("非 JSON 输出：整段原文降级为 reply，情感/资源置空")
    void shouldFallbackToPlainText() {
        String raw = "这不是JSON，就是一句温柔的回复。";

        ParsedReply reply = parser.parse(raw);

        assertThat(reply.reply()).isEqualTo(raw);
        assertThat(reply.sentimentScore()).isNull();
        assertThat(reply.resources()).isEmpty();
    }

    @Test
    @DisplayName("JSON 缺少 reply 字段：按纯文本降级避免空回复")
    void shouldFallbackWhenReplyMissing() {
        String raw = "{\"sentimentScore\": 0.5, \"resources\": []}";

        ParsedReply reply = parser.parse(raw);

        assertThat(reply.reply()).isEqualTo(raw);
        assertThat(reply.sentimentScore()).isNull();
    }

    @Test
    @DisplayName("空输入：返回空 reply")
    void shouldHandleBlankInput() {
        ParsedReply reply = parser.parse("   ");

        assertThat(reply.reply()).isEmpty();
        assertThat(reply.sentimentScore()).isNull();
        assertThat(reply.resources()).isEqualTo(List.of());
    }
}
