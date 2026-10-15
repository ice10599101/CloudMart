package com.cloudmart.software.dto.software;

import java.time.Instant;
import java.util.List;

/**
 * 互动资格上下文（方案 9.6）：双向拉黑结论；canInteract=false 时
 * blockedPeerIds 非空。命中者内容不返回，可见数量以原始候选分页为准。
 */
public record SoftwareInteractionContextVO(
        Long userId,
        boolean canInteract,
        String reasonCode,
        List<Long> blockedPeerIds,
        Instant checkedAt) {
}
