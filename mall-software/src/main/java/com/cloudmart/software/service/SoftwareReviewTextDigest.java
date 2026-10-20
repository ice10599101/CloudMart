package com.cloudmart.software.service;

import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * SW04：提交审核文本与内容摘要的唯一实现。
 *
 * <p>提交事务内冻结内容并记录摘要，异步检查读取同一份冻结记录重算；
 * 两边必须使用完全相同的文本收集顺序（介绍→亮点→版本日志→来源说明→名称兜底）
 * 与分段规则，摘要不一致表示内容被篡改或代码版本不一致，不得继续审核。</p>
 */
public final class SoftwareReviewTextDigest {

    private static final int MAX_TEXTS = 100;
    private static final int MAX_SEGMENT_LENGTH = 20_000;

    private SoftwareReviewTextDigest() {
    }

    /** 收集审核纯文本（顺序稳定，不含 URL/提取码/密码）。 */
    public static List<String> collect(SoftwareRevision snapshot, List<SoftwareRelease> releases,
                                       List<SoftwareSource> sources) {
        List<String> texts = new ArrayList<>();
        addReviewText(texts, snapshot.getDescriptionText());
        // UX03：短亮点属审核内容，纳入摘要（改动亮点后旧审核结论失效）
        if (snapshot.getHighlights() != null && !snapshot.getHighlights().isBlank()) {
            texts.add(snapshot.getHighlights());
        }
        for (SoftwareRelease release : releases) {
            addReviewText(texts, release.getChangelog());
        }
        for (SoftwareSource source : sources) {
            addReviewText(texts, source.getNote());
        }
        // 保持既有非空批次的顺序及摘要不变，兼容已冻结的待审快照。
        if (texts.isEmpty()) {
            addReviewText(texts, snapshot.getName());
        }
        return texts;
    }

    /** 摘要算法（SHA-256，分隔符 \u0001 与既有 content_digest 口径一致）。 */
    public static String digest(List<String> texts) {
        return sha256(String.join("\u0001", texts));
    }

    public static boolean exceedsBatchLimit(List<String> texts) {
        return texts.size() > MAX_TEXTS;
    }

    private static void addReviewText(List<String> texts, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        // 软件介绍允许 50000 字，社区单条仅允许 20000 字；分段保留全部内容。
        for (int start = 0; start < text.length(); ) {
            int end = Math.min(start + MAX_SEGMENT_LENGTH, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                    && Character.isLowSurrogate(text.charAt(end))) {
                end--;
            }
            String part = text.substring(start, end);
            if (!part.isBlank()) {
                texts.add(part);
            }
            start = end;
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
