package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 匹配推荐项（Sprint 2.6，文档 2.8 契约 + 体验要求的相似度说明）。
 *
 * @param groupId        小组 ID
 * @param keyword        组主题关键词
 * @param memberCount    当前人数
 * @param maxMembers     容量
 * @param leaderNickname 组长昵称（Fail-Open 占位）
 * @param leaderAvatar   组长头像
 * @param matchScore     相似度 0-1（加权：关键词/城市/活跃度）
 * @param matchReason    相似度说明（三端一致文案，如"你们都想看极光"）
 * @param status         状态（推荐列表恒为 OPEN）
 * @param cityCode       同城代理码（可空）
 * @param createdAt      建组时间（UTC）
 */
@Schema(description = "匹配推荐项")
public record MatchGroupVO(
        Long groupId,
        String keyword,
        Integer memberCount,
        Integer maxMembers,
        String leaderNickname,
        String leaderAvatar,
        Double matchScore,
        String matchReason,
        String status,
        String cityCode,
        java.time.LocalDateTime createdAt
) {

    /** 推荐分页（候选窗口内排序分页，游标为窗口偏移） */
    public record MatchPage(List<MatchGroupVO> records, String nextCursor, boolean hasMore) {
    }
}
