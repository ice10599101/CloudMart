package com.cloudmart.community.vo.software;

import java.time.Instant;
import java.util.List;

/**
 * 互动资格上下文（方案 9.6）：双向拉黑结论（我拉黑的 ∪ 拉黑我的 与对端集合交集）。
 * canInteract=false 时 blockedPeerIds 非空，reasonCode=PEER_BLOCKED。
 */
public record SoftwareInteractionContextVO(
        Long userId,
        boolean canInteract,
        String reasonCode,
        List<Long> blockedPeerIds,
        Instant checkedAt) {
}
