package com.cloudmart.software.dto.software;

import java.util.List;

/**
 * 批量内容审核请求（方案 9.6 社区契约镜像）：texts 为本服务消毒后提取的
 * 纯文本节点序列；contentHash 绑定本次请求内容，审核结果与保存动作据此核对竞态。
 */
public record SoftwareReviewRequest(
        String requestId,
        String contentHash,
        List<String> texts,
        Integer mediaCount) {
}
