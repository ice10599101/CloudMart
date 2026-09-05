package com.cloudmart.wish.vo;

/**
 * 个人星光资源概览（文档 L848：GET /wish/my/resources）。
 *
 * @param balance     当前余额（wish_user_stat.starlight_balance 快照）
 * @param todayEarned 今日已获取（流水 SUM，边界为服务器写入时区当日 00:00，与 createdAt 填充时区一致）
 * @param todaySpent  今日已消耗
 */
public record MyResourcesVO(int balance, int todayEarned, int todaySpent) {
}
