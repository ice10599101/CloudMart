package com.cloudmart.software.dto.software;

import java.util.List;

/**
 * 互动资格上下文请求（方案 9.6 社区契约镜像）：评论列表按候选作者批量查询。
 */
public record InteractionContextRequest(
        Long userId,
        List<Long> peerUserIds) {
}
