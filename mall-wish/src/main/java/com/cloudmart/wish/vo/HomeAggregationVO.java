package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 心愿宇宙首页聚合 VO（对应文档 2.18 GET /wish/home 响应）。
 *
 * <p>首页一次请求返回所有模块的轻量摘要，避免客户端多次请求。
 * 各模块详情走对应独立 API。</p>
 *
 * <p>Sprint 1.1 简化版规则：</p>
 * <ul>
 *   <li>{@code worldTree} 返回 null（Sprint 2.1 上线）</li>
 *   <li>{@code todayRecommend} 返回 5 条（互动量 0.5 + 时效性 0.3 + 多样性 0.2）</li>
 *   <li>{@code myWishes} 已登录用户返回 3 条摘要；未登录返回空数组</li>
 *   <li>{@code hotResonance} 返回 5 条（复用 wish:hot:feed ZSet Top 5）</li>
 *   <li>{@code entries.wishEntry}=true，{@code mapEntry/aiAssistantEntry}=false</li>
 * </ul>
 */
@Schema(name = "HomeAggregationVO", description = "心愿宇宙首页聚合数据")
public record HomeAggregationVO(
        @Schema(description = "世界树聚合状态（Sprint 2.1 前返回 null）") Object worldTree,
        @Schema(description = "今日推荐心愿 5 条") List<TodayRecommendItemVO> todayRecommend,
        @Schema(description = "我的心愿 3 条摘要（未登录返回空数组）") List<MyWishSummaryVO> myWishes,
        @Schema(description = "热门共鸣 5 条") List<HotResonanceItemVO> hotResonance,
        @Schema(description = "入口开关") HomeEntriesVO entries
) {}
