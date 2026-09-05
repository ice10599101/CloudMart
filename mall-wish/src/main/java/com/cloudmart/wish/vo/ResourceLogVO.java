package com.cloudmart.wish.vo;

import com.cloudmart.wish.entity.WishResourceLog;
import com.cloudmart.wish.enums.ResourceLogSource;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 星光流水条目（文档 L848：GET /wish/my/resources/logs）。
 *
 * @param id           流水 ID（雪花，同时为游标）
 * @param type         EARN / SPEND
 * @param amount       数量（恒为正数，方向由 type 表达）
 * @param reason       业务来源中文描述（机器可读 source 不外泄）
 * @param balanceAfter 操作后余额快照
 * @param createdAt    发生时间
 */
public record ResourceLogVO(Long id, String type, int amount, String reason,
                            Integer balanceAfter, LocalDateTime createdAt) {

    private static final Map<ResourceLogSource, String> SOURCE_LABELS = Map.ofEntries(
            Map.entry(ResourceLogSource.SIGNIN, "每日签到"),
            Map.entry(ResourceLogSource.LIGHTED, "心愿被点亮"),
            Map.entry(ResourceLogSource.SAME_WISHED, "被同求"),
            Map.entry(ResourceLogSource.CHECKIN, "心愿打卡"),
            Map.entry(ResourceLogSource.FULFILL, "还愿奖励"),
            Map.entry(ResourceLogSource.LIGHT_OTHER, "点亮他人"),
            Map.entry(ResourceLogSource.ANON_STAR, "匿名星光"),
            Map.entry(ResourceLogSource.EXCHANGE, "工坊兑换"),
            Map.entry(ResourceLogSource.ACTIVITY_REWARD, "活动奖励"));

    public static ResourceLogVO from(WishResourceLog log) {
        String source = log.getSource();
        String reason = null;
        if (source != null) {
            try {
                reason = SOURCE_LABELS.getOrDefault(ResourceLogSource.valueOf(source), source);
            } catch (IllegalArgumentException ex) {
                reason = source; // 未知来源透传原始值，不抛错
            }
        }
        return new ResourceLogVO(
                log.getId(),
                log.getType() == null ? null : log.getType().name(),
                log.getDelta() == null ? 0 : Math.abs(log.getDelta()),
                reason,
                log.getBalanceAfter(),
                log.getCreatedAt());
    }
}
