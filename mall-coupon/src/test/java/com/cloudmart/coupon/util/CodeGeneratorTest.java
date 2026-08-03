package com.cloudmart.coupon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeGenerator 单元测试
 * <p>
 * 覆盖生成-校验往返、边界值、非法输入、字符集约束等场景。
 * </p>
 */
@DisplayName("CodeGenerator 兑换码生成器测试")
class CodeGeneratorTest {

    private final CodeGenerator codeGenerator = new CodeGenerator();

    @Nested
    @DisplayName("generate 方法")
    class GenerateTests {

        @Test
        @DisplayName("生成的兑换码可通过校验")
        void shouldGenerateValidatableCode() {
            String code = codeGenerator.generate(1L);
            assertTrue(codeGenerator.validate(code), "生成的码应通过校验");
        }

        @Test
        @DisplayName("不同序列号生成不同兑换码")
        void shouldGenerateDifferentCodesForDifferentSequences() {
            String code1 = codeGenerator.generate(1L);
            String code2 = codeGenerator.generate(2L);
            assertNotEquals(code1, code2);
        }

        @Test
        @DisplayName("连续序列号无碰撞")
        void shouldNotCollideForSequentialSequences() {
            java.util.Set<String> codes = new java.util.HashSet<>();
            for (long seq = 1; seq <= 1000; seq++) {
                String code = codeGenerator.generate(seq);
                assertTrue(codes.add(code), "序列号 " + seq + " 生成了重复兑换码: " + code);
                assertTrue(codeGenerator.validate(code), "序列号 " + seq + " 的兑换码校验失败");
            }
        }

        @Test
        @DisplayName("大序列号也能正常生成与校验")
        void shouldHandleLargeSequence() {
            long largeSeq = 999_999_999L;
            String code = codeGenerator.generate(largeSeq);
            assertTrue(codeGenerator.validate(code));
            assertEquals(largeSeq, codeGenerator.extractSequence(code));
        }

        @Test
        @DisplayName("序列号为0时抛出异常")
        void shouldThrowForZeroSequence() {
            assertThrows(IllegalArgumentException.class, () -> codeGenerator.generate(0L));
        }

        @Test
        @DisplayName("负序列号抛出异常")
        void shouldThrowForNegativeSequence() {
            assertThrows(IllegalArgumentException.class, () -> codeGenerator.generate(-1L));
        }
    }

    @Nested
    @DisplayName("validate 方法")
    class ValidateTests {

        @Test
        @DisplayName("null 输入返回 false")
        void shouldReturnFalseForNull() {
            assertFalse(codeGenerator.validate(null));
        }

        @Test
        @DisplayName("空字符串返回 false")
        void shouldReturnFalseForEmpty() {
            assertFalse(codeGenerator.validate(""));
        }

        @Test
        @DisplayName("含易混淆字符 0 的兑换码校验失败")
        void shouldRejectCharacterZero() {
            String code = codeGenerator.generate(1L);
            String tampered = code.replace(code.charAt(0), '0');
            assertFalse(codeGenerator.validate(tampered), "含字符0的兑换码应校验失败");
        }

        @Test
        @DisplayName("含易混淆字符 1 的兑换码校验失败")
        void shouldRejectCharacterOne() {
            String code = codeGenerator.generate(1L);
            String tampered = code.replace(code.charAt(0), '1');
            assertFalse(codeGenerator.validate(tampered), "含字符1的兑换码应校验失败");
        }

        @Test
        @DisplayName("含易混淆字符 I 的兑换码校验失败")
        void shouldRejectCharacterI() {
            String code = codeGenerator.generate(1L);
            String tampered = code.replace(code.charAt(0), 'I');
            assertFalse(codeGenerator.validate(tampered), "含字符I的兑换码应校验失败");
        }

        @Test
        @DisplayName("含易混淆字符 O 的兑换码校验失败")
        void shouldRejectCharacterO() {
            String code = codeGenerator.generate(1L);
            String tampered = code.replace(code.charAt(0), 'O');
            assertFalse(codeGenerator.validate(tampered), "含字符O的兑换码应校验失败");
        }

        @Test
        @DisplayName("篡改校验位后校验失败")
        void shouldRejectTamperedChecksum() {
            String code = codeGenerator.generate(42L);
            // 通过修改最后一个字符来篡改校验位
            char lastChar = code.charAt(code.length() - 1);
            char newChar = lastChar == 'A' ? 'B' : 'A';
            String tampered = code.substring(0, code.length() - 1) + newChar;
            assertFalse(codeGenerator.validate(tampered), "篡改后的兑换码应校验失败");
        }
    }

    @Nested
    @DisplayName("extractSequence 方法")
    class ExtractSequenceTests {

        @Test
        @DisplayName("能正确提取序列号")
        void shouldExtractCorrectSequence() {
            long seq = 12345L;
            String code = codeGenerator.generate(seq);
            assertEquals(seq, codeGenerator.extractSequence(code));
        }

        @Test
        @DisplayName("null 输入返回 null")
        void shouldReturnNullForNullInput() {
            assertNull(codeGenerator.extractSequence(null));
        }

        @Test
        @DisplayName("空字符串返回 null")
        void shouldReturnNullForEmptyInput() {
            assertNull(codeGenerator.extractSequence(""));
        }

        @Test
        @DisplayName("含非法字符返回 null")
        void shouldReturnNullForInvalidCharacter() {
            assertNull(codeGenerator.extractSequence("ABC0DEF"));
        }
    }

    @Test
    @DisplayName("生成的兑换码全部由合法字符组成")
    void shouldOnlyContainLegalCharacters() {
        String legalChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        for (long seq = 1; seq <= 500; seq++) {
            String code = codeGenerator.generate(seq);
            for (char c : code.toCharArray()) {
                assertTrue(legalChars.indexOf(c) >= 0,
                        "兑换码 " + code + " 含非法字符: " + c);
            }
        }
    }
}
