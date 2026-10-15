package com.cloudmart.community.dto.software;

import java.util.List;

/**
 * 小答软件互动资格上下文请求（方案 9.6）。
 *
 * <p>评论列表按候选作者 ID 批量查询（不逐条调用）；社区只返回拉黑结论，
 * 账号状态等身份事实由软件服务经用户服务适配器另行核对。</p>
 *
 * @param userId      当前用户
 * @param peerUserIds 候选对端用户 ID（1~100 个）
 */
public record InteractionContextRequest(
        Long userId,
        List<Long> peerUserIds) {
}
