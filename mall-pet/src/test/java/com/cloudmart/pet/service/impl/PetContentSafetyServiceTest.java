package com.cloudmart.pet.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetMetrics;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.PetContentSensitiveWord;
import com.cloudmart.pet.repository.PetContentSensitiveWordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * P0-1 内容安全服务单元测试：多模式匹配 / 打码 / 危机词兜底 / 词库降级。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetContentSafetyService 单元测试")
class PetContentSafetyServiceTest {

    @Mock
    private PetContentSensitiveWordMapper wordMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PetMetrics metrics;

    private PetContentSafetyService service;

    @BeforeEach
    void setUp() {
        service = new PetContentSafetyService(wordMapper, jdbcTemplate, new PetProperties(), metrics);
    }

    /** 模拟词库指纹查询 + 全量加载（refresh 路径） */
    private void loadWords(PetContentSensitiveWord... words) {
        lenient().when(jdbcTemplate.queryForMap(anyString()))
                .thenReturn(Map.of("cnt", words.length, "max_id", 1L, "max_updated", "2026-01-01"));
        lenient().when(wordMapper.selectList(any())).thenReturn(List.of(words));
        service.refresh();
    }

    private PetContentSensitiveWord word(String text, String category) {
        PetContentSensitiveWord row = new PetContentSensitiveWord();
        row.setId(1L);
        row.setWord(text);
        row.setCategory(category);
        row.setStatus(1);
        return row;
    }

    @Test
    @DisplayName("check：命中返回词与类别")
    void checkHitsAndCategorizes() {
        loadWords(word("免费领红包", "AD"), word("傻逼", "ABUSE"));

        var hit = service.check("快点来免费领红包啦");
        assertThat(hit).isPresent();
        assertThat(hit.get().word()).isEqualTo("免费领红包");
        assertThat(hit.get().category()).isEqualTo("AD");
    }

    @Test
    @DisplayName("check：英文词忽略大小写")
    void checkIsCaseInsensitive() {
        loadWords(word("casino", "AD"));

        assertThat(service.check("Welcome to CASINO night")).isPresent();
        assertThat(service.check("welcome to casino night")).isPresent();
    }

    @Test
    @DisplayName("check：干净文本不命中")
    void checkCleanText() {
        loadWords(word("傻逼", "ABUSE"));

        assertThat(service.check("今天天气真好")).isEmpty();
        assertThat(service.check(null)).isEmpty();
        assertThat(service.check("")).isEmpty();
    }

    @Test
    @DisplayName("filter：命中区间等长打码，未命中区域原样保留")
    void filterMasksHitsOnly() {
        loadWords(word("傻逼", "ABUSE"), word("滚蛋", "ABUSE"));

        String filtered = service.filter("你这个傻逼快点滚蛋吧");
        assertThat(filtered).isEqualTo("你这个**快点**吧");
        assertThat(filtered).doesNotContain("傻逼").doesNotContain("滚蛋");
    }

    @Test
    @DisplayName("filter：无命中返回原文（同一引用语义）")
    void filterKeepsCleanTextIntact() {
        loadWords(word("傻逼", "ABUSE"));

        assertThat(service.filter("今天天气真好")).isEqualTo("今天天气真好");
    }

    @Test
    @DisplayName("filter：重叠命中全部打码")
    void filterOverlappingMatches() {
        loadWords(word("abc", "AD"), word("bc", "AD"));

        assertThat(service.filter("xxabcyy")).isEqualTo("xx***yy");
    }

    @Test
    @DisplayName("isCrisis：CRISIS 类词库命中")
    void crisisFromWordList() {
        loadWords(word("不想活", "CRISIS"));

        assertThat(service.isCrisis("最近有点不想活了")).isTrue();
        assertThat(service.isCrisis("今天很开心")).isFalse();
    }

    @Test
    @DisplayName("isCrisis：词库为空时配置危机词兜底（Fail-Open 底线）")
    void crisisConfigFallback() {
        loadWords(); // 空词库（指纹有效、零词条）

        assertThat(service.isCrisis("我想自杀")).isTrue();
    }

    @Test
    @DisplayName("词库刷新失败：沿用旧自动机（不白屏 UGC）")
    void refreshFailureKeepsPreviousSnapshot() {
        loadWords(word("傻逼", "ABUSE"));
        assertThat(service.check("真是傻逼")).isPresent();

        // 指纹查询与全量加载同时失败 → 降级保留旧词库
        when(jdbcTemplate.queryForMap(anyString())).thenThrow(new RuntimeException("db down"));
        when(wordMapper.selectList(any())).thenThrow(new RuntimeException("db down"));
        service.refresh();

        assertThat(service.check("真是傻逼")).isPresent();
    }

    @Test
    @DisplayName("词库从未加载：check 放空（Fail-Open），由调用方放行")
    void neverLoadedFailsOpen() {
        assertThat(service.check("任意文本")).isEmpty();
    }

    @Test
    @DisplayName("requireCleanPetName：宠物名命中抛 PET_NAME_SENSITIVE")
    void petNameRejection() {
        loadWords(word("傻逼", "ABUSE"));

        assertThatThrownBy(() -> service.requireCleanPetName("我是傻逼"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("这个名字不太合适");
        // 干净名字不抛
        service.requireCleanPetName("小橘");
    }

    @Test
    @DisplayName("自动机：单词与多模式命中长度正确（fail 链回退）")
    void automatonFailLinkFallback() {
        PetContentSafetyService.Automaton automaton = PetContentSafetyService.Automaton.build(
                List.of("he", "she", "his", "hers"));

        assertThat(automaton.firstMatch("ushers")).contains("she");
        assertThat(automaton.firstMatch("this")).contains("his");
        assertThat(automaton.firstMatch("hello")).contains("he");
        assertThat(automaton.firstMatch("world")).isEmpty();
    }
}
