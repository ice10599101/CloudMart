package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.config.PetMetrics;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.PetContentSensitiveWord;
import com.cloudmart.pet.repository.PetContentSensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 宠物内容安全服务（P0-1）：多模式敏感词匹配 + 打码 + 危机词识别。
 *
 * <p>匹配引擎为内存 Aho-Corasick 自动机（自研实现，零第三方依赖：社区版
 * {@code org.ahocorasick:ahocorasick} 自 2017 年起无维护，不符合依赖准入要求）。
 * 词库来源 {@code pet_content_sensitive_word} 表（管理端可热更新）：</p>
 *
 * <ul>
 *   <li>启动加载 + 每 60s 按"指纹"（行数/最大 id/最大 updated_at）轮询，变更 ≤1 分钟全实例生效；
 *       管理端写路径额外主动 refresh，本实例即时生效；</li>
 *   <li>{@link #check}：返回首个命中（词 + 类别），供调用方拒绝；</li>
 *   <li>{@link #filter}：命中区间打码为 {@code *}（保留审核线索），无命中原样返回；</li>
 *   <li>{@link #isCrisis}：CRISIS 类命中或配置危机词兜底命中（词库加载失败时仍有底线拦截）。</li>
 * </ul>
 *
 * <p>降级策略（显式声明）：词库加载失败时沿用最近一次成功的自动机；从未成功加载则
 * 匹配放空（Fail-Open，与模块内限频降级风格一致），此时 {@link #isCrisis} 仍由
 * {@code pet.chat.crisisKeywords} 配置兜底，并打 {@code pet_content_safety_degraded} 指标。</p>
 */
@Component
@Slf4j
public class PetContentSafetyService {

    /** 敏感词类别（与 pet_content_sensitive_word.category ENUM 对齐） */
    public static final List<String> CATEGORIES = List.of("POLITICS", "ABUSE", "AD", "CRISIS");

    private final PetContentSensitiveWordMapper wordMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PetProperties properties;
    private final PetMetrics metrics;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    private record Snapshot(String fingerprint, Map<String, String> categoryByWord, Automaton automaton) {
        static final Snapshot EMPTY = new Snapshot("", Map.of(), Automaton.empty());
    }

    public PetContentSafetyService(PetContentSensitiveWordMapper wordMapper,
                                   JdbcTemplate jdbcTemplate,
                                   PetProperties properties,
                                   PetMetrics metrics) {
        this.wordMapper = wordMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    @PostConstruct
    void loadOnStartup() {
        refresh();
    }

    /** 词库热更新：指纹比对，变更才重建自动机（60s 轮询 + 管理端写后主动调用） */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void refresh() {
        String fingerprint = loadFingerprint();
        Snapshot current = snapshot.get();
        if (current.fingerprint().equals(fingerprint)) {
            return;
        }
        try {
            Map<String, String> categoryByWord = new LinkedHashMap<>();
            wordMapper.selectList(new LambdaQueryWrapper<PetContentSensitiveWord>()
                            .eq(PetContentSensitiveWord::getStatus, 1))
                    .forEach(word -> categoryByWord.put(normalize(word.getWord()), word.getCategory()));
            Automaton automaton = Automaton.build(categoryByWord.keySet());
            snapshot.set(new Snapshot(fingerprint, Map.copyOf(categoryByWord), automaton));
            log.info("敏感词库已加载: fingerprint={}, wordCount={}", fingerprint, categoryByWord.size());
        } catch (Exception e) {
            // Fail-Open：沿用旧自动机（可能为空），危机词仍由配置兜底
            metrics.increment("pet_content_safety_degraded", "stage", "refresh");
            log.warn("敏感词库刷新失败（沿用旧词库）: fingerprint={}", fingerprint, e);
        }
    }

    /**
     * 内容检查：命中返回（词 + 类别），未命中或词库不可用返回 empty。
     * 调用方（宠物名/留言墙）据此拒绝发布。
     */
    public Optional<Hit> check(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Snapshot current = snapshot.get();
        if (current.fingerprint().isEmpty()) {
            // 词库从未成功加载：无匹配能力，调用方 Fail-Open 放行（危机词另有配置兜底）
            return Optional.empty();
        }
        String normalized = normalize(text);
        return current.automaton().firstMatch(normalized)
                .map(word -> new Hit(word, current.categoryByWord().getOrDefault(word, "ABUSE")));
    }

    /** 打码过滤：命中区间替换为 '*'（等长保留文本结构），无命中原样返回 */
    public String filter(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Snapshot current = snapshot.get();
        if (current.fingerprint().isEmpty()) {
            return text;
        }
        List<int[]> maskedIntervals = new ArrayList<>();
        current.automaton().allMatches(normalize(text),
                (end, length) -> maskedIntervals.add(new int[]{end - length + 1, end}));
        if (maskedIntervals.isEmpty()) {
            return text;
        }
        char[] chars = text.toCharArray();
        for (int[] interval : maskedIntervals) {
            for (int i = Math.max(interval[0], 0); i <= interval[1] && i < chars.length; i++) {
                chars[i] = '*';
            }
        }
        return new String(chars);
    }

    /** 危机词识别：CRISIS 类命中或配置危机词兜底（词库加载失败仍有底线拦截） */
    public boolean isCrisis(String text) {
        return check(text).map(Hit::category).map("CRISIS"::equals).orElse(false)
                || containsConfigCrisisKeyword(text);
    }

    /** 宠物名内容检查：命中即拒绝（领养/改名共用口径，P0-1） */
    public void requireCleanPetName(String name) {
        check(name).ifPresent(hit -> {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_NAME_SENSITIVE,
                    "这个名字不太合适，换一个吧");
        });
    }

    /** 词库不可用时的底线兜底（pet.chat.crisisKeywords 配置） */
    private boolean containsConfigCrisisKeyword(String message) {
        for (String keyword : properties.getChat().getCrisisKeywords()) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 词库指纹：启用行数 + 最大 id + 最大 updated_at（覆盖增/删/改三类变更） */
    private String loadFingerprint() {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) AS cnt, COALESCE(MAX(id), 0) AS max_id, "
                            + "COALESCE(MAX(updated_at), '1970-01-01') AS max_updated "
                            + "FROM pet_content_sensitive_word WHERE status = 1");
            return row.get("cnt") + ":" + row.get("max_id") + ":" + row.get("max_updated");
        } catch (Exception e) {
            return "";
        }
    }

    /** 匹配归一化：逐字符小写（保证索引 1:1，打码区间可直接映射回原文） */
    private static String normalize(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Character.toLowerCase(chars[i]);
        }
        return new String(chars);
    }

    /** 命中结果（词为归一化小写形式） */
    public record Hit(String word, String category) {
    }

    /**
     * Aho-Corasick 自动机（.goto/fail/output 标准实现）。
     * 词库规模（百~千级）与文本长度（≤2000 字符）下，逐 fail 链收集输出即可，
     * 无需 output 链接优化；每次词库刷新全量重建，运行期只读。
     */
    static final class Automaton {

        private final List<Map<Character, Integer>> next = new ArrayList<>();
        private final List<Integer> fail = new ArrayList<>();
        private final List<Map<String, Integer>> outputs = new ArrayList<>();
        /** 输出词 → 词长（避免扫描期重复 length 计算） */
        private final Map<String, Integer> wordLengths = new HashMap<>();

        private Automaton() {
            newNode();
        }

        static Automaton empty() {
            return new Automaton();
        }

        static Automaton build(Iterable<String> words) {
            Automaton automaton = new Automaton();
            for (String word : words) {
                if (word == null || word.isEmpty()) {
                    continue;
                }
                automaton.insert(word);
            }
            automaton.buildFailLinks();
            return automaton;
        }

        private void insert(String word) {
            int current = 0;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                Integer child = next.get(current).get(c);
                if (child == null) {
                    child = newNode();
                    next.get(current).put(c, child);
                }
                current = child;
            }
            outputs.get(current).put(word, word.length());
            wordLengths.put(word, word.length());
        }

        private int newNode() {
            next.add(new HashMap<>());
            fail.add(0);
            outputs.add(new HashMap<>());
            return next.size() - 1;
        }

        private void buildFailLinks() {
            ArrayDequeLike queue = new ArrayDequeLike(next.size());
            for (Integer child : next.get(0).values()) {
                fail.set(child, 0);
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                int parent = queue.poll();
                for (Map.Entry<Character, Integer> edge : next.get(parent).entrySet()) {
                    char c = edge.getKey();
                    int child = edge.getValue();
                    int fallback = fail.get(parent);
                    while (fallback != 0 && !next.get(fallback).containsKey(c)) {
                        fallback = fail.get(fallback);
                    }
                    Integer candidate = next.get(fallback).get(c);
                    fail.set(child, candidate != null && candidate != child ? candidate : 0);
                    outputs.get(child).putAll(outputs.get(fail.get(child)));
                    queue.add(child);
                }
            }
        }

        /** 扫描并返回首个命中词（无命中返回 empty） */
        Optional<String> firstMatch(String normalizedText) {
            int state = 0;
            for (int i = 0; i < normalizedText.length(); i++) {
                state = step(state, normalizedText.charAt(i));
                if (!outputs.get(state).isEmpty()) {
                    return Optional.of(outputs.get(state).keySet().iterator().next());
                }
            }
            return Optional.empty();
        }

        /** 扫描并回调全部命中区间（endIndex 为结束下标，闭区间；重叠命中都回调，打码侧并集处理） */
        void allMatches(String normalizedText, MatchConsumer onMatch) {
            int state = 0;
            for (int i = 0; i < normalizedText.length(); i++) {
                state = step(state, normalizedText.charAt(i));
                for (Integer length : outputs.get(state).values()) {
                    onMatch.accept(i, length);
                }
            }
        }

        private int step(int state, char c) {
            while (state != 0 && !next.get(state).containsKey(c)) {
                state = fail.get(state);
            }
            Integer target = next.get(state).get(c);
            return target != null ? target : 0;
        }

        @FunctionalInterface
        interface MatchConsumer {
            void accept(int endIndex, int wordLength);
        }

        /** 极简 int 队列（BFS 专用，避免装箱） */
        private static final class ArrayDequeLike {
            private int[] data;
            private int head;
            private int tail;

            private ArrayDequeLike(int capacity) {
                this.data = new int[Math.max(16, capacity)];
            }

            private void add(int value) {
                if (tail == data.length) {
                    data = java.util.Arrays.copyOf(data, data.length * 2);
                }
                data[tail++] = value;
            }

            private boolean isEmpty() {
                return head == tail;
            }

            private int poll() {
                return data[head++];
            }
        }
    }
}
