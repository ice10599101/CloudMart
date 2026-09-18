package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理端漂流瓶 VO（审核/治理视角）：始终包含投瓶人与捞瓶人真实用户 ID（不脱敏），
 * 匿名开关状态原样透出供管理端溯源；展示层仍按匿名规则面向用户。
 *
 * @param id                漂流瓶 ID
 * @param content           瓶内文字（富文本 HTML；关联心愿时为 null）
 * @param wishId            关联心愿 ID
 * @param wishTitle         关联心愿标题快照
 * @param status            物理状态：FLOATING / PICKED / RETURNED
 * @param isAnonymous       投瓶是否匿名
 * @param throwerUserId     投瓶人用户 ID（真实身份）
 * @param throwerNickname   投瓶人昵称（Feign 失败降级占位）
 * @param pickerIsAnonymous 捞瓶人是否匿名
 * @param pickerUserId      捞瓶人用户 ID（未捞起为 null）
 * @param pickerNickname    捞瓶人昵称（未捞起/降级为 null）
 * @param isCollected       是否已被捞起人收藏
 * @param returnCount       被扔回海里次数
 * @param isHidden          是否已下架
 * @param commentCount      评论数（含回复）
 * @param thrownAt          投瓶时间
 * @param pickedAt          捞瓶时间
 */
@Schema(description = "管理端漂流瓶")
public record AdminDriftBottleVO(
        Long id,
        String content,
        Long wishId,
        String wishTitle,
        String status,
        Boolean isAnonymous,
        Long throwerUserId,
        String throwerNickname,
        Boolean pickerIsAnonymous,
        Long pickerUserId,
        String pickerNickname,
        Boolean isCollected,
        Integer returnCount,
        Boolean isHidden,
        Long commentCount,
        LocalDateTime thrownAt,
        LocalDateTime pickedAt
) {
}
