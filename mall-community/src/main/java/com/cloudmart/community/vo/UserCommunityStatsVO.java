package com.cloudmart.community.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户社区数据面板VO")
public record UserCommunityStatsVO(
    @Schema(description = "获赞总数（他人给 TA 的帖子点的赞，仅统计已发布帖子）") Long likesReceived,
    @Schema(description = "TA 的评论数（TA 在社区发表的评论总数）") Long commentsMade,
    @Schema(description = "TA 赞过数（TA 点赞的帖子总数）") Long likesGiven
) {}
