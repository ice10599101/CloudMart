package com.cloudmart.software.service;

import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SW04 冻结内容摘要：文本收集顺序、UTF-16 安全分段与摘要稳定性。
 * 提交与异步检查必须得到完全相同的文本序列，否则摘要不一致会误判篡改。
 */
class SoftwareReviewTextDigestTest {

    private SoftwareRevision revision(String description, String highlights, String name) {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setDescriptionText(description);
        revision.setHighlights(highlights);
        revision.setName(name);
        return revision;
    }

    private SoftwareRelease release(Long id, String changelog) {
        SoftwareRelease release = new SoftwareRelease();
        release.setId(id);
        release.setChangelog(changelog);
        return release;
    }

    private SoftwareSource source(String note) {
        SoftwareSource source = new SoftwareSource();
        source.setNote(note);
        return source;
    }

    @Test
    @DisplayName("SW04：按介绍→亮点→版本日志→来源说明收集，忽略空白且不含名称兜底")
    void collectsTextsInStableOrder() {
        List<String> texts = SoftwareReviewTextDigest.collect(
                revision("介绍", "亮点", "名称"),
                List.of(release(1L, "版本一"), release(2L, " ")),
                List.of(source("来源一"), source(null)));

        assertThat(texts).containsExactly("介绍", "亮点", "版本一", "来源一");
    }

    @Test
    @DisplayName("SW04：全部审核文本为空时以名称兜底，避免空审核批次")
    void fallsBackToNameWhenEmpty() {
        List<String> texts = SoftwareReviewTextDigest.collect(
                revision(null, " ", "软件名"), List.of(), List.of());

        assertThat(texts).containsExactly("软件名");
    }

    @Test
    @DisplayName("SW04：超过 20000 字分段且不切断代理对（emoji 不产生乱码）")
    void segmentsWithoutSplittingSurrogatePairs() {
        String emoji = "😀"; // 2 个 UTF-16 单元
        String text = "a".repeat(19_999) + emoji + "b";
        List<String> texts = SoftwareReviewTextDigest.collect(
                revision(text, null, "名称"), List.of(), List.of());

        assertThat(texts).hasSize(2);
        assertThat(texts.get(0)).isEqualTo("a".repeat(19_999));
        assertThat(texts.get(1)).isEqualTo(emoji + "b");
        assertThat(texts.get(0) + texts.get(1)).isEqualTo(text);
    }

    @Test
    @DisplayName("SW04：摘要稳定（同内容同 hash）且内容变化即变化")
    void digestIsStableAndContentSensitive() {
        String a = SoftwareReviewTextDigest.digest(List.of("介绍", "版本一"));
        String b = SoftwareReviewTextDigest.digest(List.of("介绍", "版本一"));
        String changed = SoftwareReviewTextDigest.digest(List.of("介绍", "版本二"));

        assertThat(a).hasSize(64).isEqualTo(b);
        assertThat(changed).isNotEqualTo(a);
        // 分隔符口径固定：不同分组不得碰撞
        assertThat(SoftwareReviewTextDigest.digest(List.of("ab", "c")))
                .isNotEqualTo(SoftwareReviewTextDigest.digest(List.of("a", "bc")));
    }

    @Test
    @DisplayName("SW04：文本条数上限 100，超出由调用方拒绝提交")
    void batchLimit() {
        List<String> ok = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ok.add("文本" + i);
        }
        List<String> tooMany = new ArrayList<>(ok);
        tooMany.add("额外");

        assertThat(SoftwareReviewTextDigest.exceedsBatchLimit(ok)).isFalse();
        assertThat(SoftwareReviewTextDigest.exceedsBatchLimit(tooMany)).isTrue();
    }
}
