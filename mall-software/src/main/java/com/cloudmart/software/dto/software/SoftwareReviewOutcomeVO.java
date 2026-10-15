package com.cloudmart.software.dto.software;

import java.time.Instant;
import java.util.List;

/**
 * 批量内容审核结果（方案 9.6）：filteredTexts 与请求 texts 同序同长；
 * approved=false 存在拒绝级违规；needsManualReview=true 转人工。
 */
public record SoftwareReviewOutcomeVO(
        String contentHash,
        boolean approved,
        boolean needsManualReview,
        List<String> filteredTexts,
        String reason,
        Instant checkedAt) {
}
