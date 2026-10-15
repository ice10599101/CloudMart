package com.cloudmart.community.dto.software;

import java.util.List;

/**
 * 小答软件批量内容审核请求（方案 9.6）。
 *
 * <p>texts 为软件服务消毒后提取的纯文本节点序列（社区不解析软件 HTML）；
 * contentHash 由软件服务生成并绑定本次请求，社区原样回传供其核对
 * "审核旧内容后保存新内容"的竞态。</p>
 *
 * @param requestId  请求追踪 ID（8~128 字符）
 * @param contentHash 内容绑定哈希（8~128 字符，软件服务生成）
 * @param texts      文本节点序列（1~100 条，每条 1~20000 字符）
 * @param mediaCount 正文媒体数量（>0 时整体强制转人工，与社区媒体审核语义一致）
 */
public record SoftwareReviewRequest(
        String requestId,
        String contentHash,
        List<String> texts,
        Integer mediaCount) {
}
