package com.cloudmart.community.vo.software;

import java.time.Instant;
import java.util.List;

/**
 * 批量内容审核结果（方案 9.6）：filteredTexts 与请求 texts 同序同长；
 * approved=false 表示存在三级违规词（拒绝发布），needsManualReview=true 表示
 * 含二级敏感词或正文带媒体（转人工队列）。两者可同时为 false 的通过态。
 *
 * @param contentHash       原样回传的绑定哈希（调用方核对内容竞态）
 * @param approved          文本是否全部通过（无三级违规）
 * @param needsManualReview 是否需人工审核（二级词或媒体）
 * @param filteredTexts     逐条过滤后的文本（一级词替换为 *）
 * @param reason            首个拦截/转人工原因（通过时为 null）
 * @param checkedAt         审核时点
 */
public record SoftwareReviewOutcomeVO(
        String contentHash,
        boolean approved,
        boolean needsManualReview,
        List<String> filteredTexts,
        String reason,
        Instant checkedAt) {
}
