package com.cloudmart.community.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户社区数据面板VO（仅统计已发布帖子）")
public record UserCommunityStatsVO(
    @Schema(description = "获赞总数") Long likesReceived,
    @Schema(description = "收到评论总数") Long commentsReceived,
    @Schema(description = "内容浏览总数") Long viewsTotal
) {}
