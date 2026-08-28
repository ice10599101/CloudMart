package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 排行榜条目（Sprint 2.7，文档 2.9 契约 + 排名变化动效）。
 *
 * @param rank       当前排名（1 起）
 * @param userId     心愿维度为心愿作者；用户维度为本人
 * @param nickname   昵称（Fail-Open 占位）
 * @param avatar     头像
 * @param score      榜单分数（light_count/bless_count/打卡天数/帮助次数）
 * @param extra      榜单附加信息：wishTitle（心愿榜）/checkinDays、helpedCount（用户榜）
 * @param rankDelta  排名变化：UP/DOWN/FLAT/NEW（三端动效一致依据）
 */
@Schema(description = "排行榜条目")
public record LeaderboardEntryVO(
        long rank,
        Long userId,
        String nickname,
        String avatar,
        Double score,
        Map<String, Object> extra,
        String rankDelta
) {
}
