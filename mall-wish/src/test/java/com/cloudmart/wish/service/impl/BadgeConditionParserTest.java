package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.enums.BadgeConditionType;
import com.cloudmart.wish.service.impl.BadgeConditionParser.BadgeCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BadgeConditionParser 单元测试（纯函数穷举：合法/非法/边界）。
 */
@DisplayName("BadgeConditionParser 单元测试")
class BadgeConditionParserTest {

    @Nested
    @DisplayName("parse - 解析")
    class ParseTests {

        @Test
        @DisplayName("合法 JSON：字段完整解析")
        void parse_validJson() {
            BadgeCondition condition = BadgeConditionParser.parse(
                    "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"发布第一个心愿\"}");

            assertThat(condition).isNotNull();
            assertThat(condition.type()).isEqualTo(BadgeConditionType.WISH_CREATED);
            assertThat(condition.threshold()).isEqualTo(1);
            assertThat(condition.description()).isEqualTo("发布第一个心愿");
        }

        @Test
        @DisplayName("null/空白：返回 null")
        void parse_nullOrBlank() {
            assertThat(BadgeConditionParser.parse(null)).isNull();
            assertThat(BadgeConditionParser.parse("")).isNull();
            assertThat(BadgeConditionParser.parse("  ")).isNull();
        }

        @Test
        @DisplayName("非法 JSON：返回 null 不抛异常")
        void parse_invalidJson() {
            assertThat(BadgeConditionParser.parse("not-json")).isNull();
            assertThat(BadgeConditionParser.parse("{broken")).isNull();
        }

        @Test
        @DisplayName("未知 type：返回 null")
        void parse_unknownType() {
            assertThat(BadgeConditionParser.parse(
                    "{\"type\":\"UNKNOWN\",\"threshold\":1,\"description\":\"x\"}")).isNull();
        }

        @Test
        @DisplayName("threshold 非法：0/负数/非整数/缺失 → null")
        void parse_invalidThreshold() {
            assertThat(BadgeConditionParser.parse(
                    "{\"type\":\"TOTAL_HELPED\",\"threshold\":0,\"description\":\"x\"}")).isNull();
            assertThat(BadgeConditionParser.parse(
                    "{\"type\":\"TOTAL_HELPED\",\"threshold\":-5,\"description\":\"x\"}")).isNull();
            assertThat(BadgeConditionParser.parse(
                    "{\"type\":\"TOTAL_HELPED\",\"threshold\":\"abc\",\"description\":\"x\"}")).isNull();
            assertThat(BadgeConditionParser.parse(
                    "{\"type\":\"TOTAL_HELPED\",\"description\":\"x\"}")).isNull();
        }

        @Test
        @DisplayName("description 缺失/空白：返回 null")
        void parse_blankDescription() {
            assertThat(BadgeConditionParser.parse(
                    "{\"type\":\"WISH_CREATED\",\"threshold\":1}")).isNull();
            assertThat(BadgeConditionParser.parse(
                    "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"  \"}")).isNull();
        }

        @Test
        @DisplayName("四种类型均可解析（种子徽章 Schema）")
        void parse_allSeedTypes() {
            for (BadgeConditionType type : BadgeConditionType.values()) {
                BadgeCondition condition = BadgeConditionParser.parse(
                        "{\"type\":\"" + type.name() + "\",\"threshold\":10,\"description\":\"d\"}");
                assertThat(condition).as("type=%s 应可解析", type).isNotNull();
                assertThat(condition.type()).isEqualTo(type);
            }
        }
    }

    @Nested
    @DisplayName("validate - 管理端校验")
    class ValidateTests {

        @Test
        @DisplayName("合法 JSON：返回 null")
        void validate_valid() {
            assertThat(BadgeConditionParser.validate(
                    "{\"type\":\"TOTAL_CHECKIN_DAYS\",\"threshold\":365,\"description\":\"累计打卡365天\"}"))
                    .isNull();
        }

        @Test
        @DisplayName("各类非法输入：返回可读错误信息")
        void validate_invalid() {
            assertThat(BadgeConditionParser.validate(null)).contains("不能为空");
            assertThat(BadgeConditionParser.validate("{broken")).contains("不是合法 JSON");
            assertThat(BadgeConditionParser.validate(
                    "{\"threshold\":1,\"description\":\"x\"}")).contains("type 必填");
            assertThat(BadgeConditionParser.validate(
                    "{\"type\":\"NOPE\",\"threshold\":1,\"description\":\"x\"}")).contains("type 必须为");
            assertThat(BadgeConditionParser.validate(
                    "{\"type\":\"WISH_CREATED\",\"threshold\":0,\"description\":\"x\"}"))
                    .contains("threshold 必填且为正整数");
            assertThat(BadgeConditionParser.validate(
                    "{\"type\":\"WISH_CREATED\",\"threshold\":1}")).contains("description 必填");
        }
    }
}
