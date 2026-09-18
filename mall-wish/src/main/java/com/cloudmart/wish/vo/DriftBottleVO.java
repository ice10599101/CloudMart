package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 漂流瓶（匿名化）：匿名投瓶时不透出投瓶人身份；实名投瓶时透出投瓶人昵称/头像/ID。
 * 捞瓶人同理：匿名捞瓶时对投瓶人隐藏捞瓶人身份，实名捞瓶时透出。
 *
 * <p>status 为物理状态 FLOATING / PICKED / RETURNED；「被回复/被收藏」由客户端依据
 * commentCount 与 isCollected 推导展示（PICKED + 收藏 → 被收藏；PICKED + 有评论 → 被回复）。</p>
 *
 * @param bottleId           漂流瓶 ID
 * @param content            自由匿名文字（富文本 HTML；关联心愿时为空）
 * @param wishId             关联心愿 ID（自由文字时为 null；捞起者可点击跳转心愿详情）
 * @param wishTitle          关联心愿标题（自由文字时为 null）
 * @param wishTags           关联心愿标签
 * @param status             物理状态：FLOATING / PICKED / RETURNED
 * @param role               THROWN（我投出的）/ PICKED（我捞到的）
 * @param thrownAt           投瓶时间
 * @param pickedAt           捞起时间（未捞起为 null）
 * @param isAnonymous        投瓶是否匿名（对外始终返回；匿名时不返回投瓶人信息）
 * @param throwerUserId      投瓶人用户 ID（仅实名投瓶时返回，匿名为 null）
 * @param throwerNickname    投瓶人昵称（仅实名投瓶时返回）
 * @param throwerAvatar      投瓶人头像（仅实名投瓶时返回）
 * @param pickerIsAnonymous  捞瓶人是否匿名（对外始终返回）
 * @param pickerUserId       捞瓶人用户 ID（仅实名捞瓶时返回，匿名为 null）
 * @param pickerNickname     捞瓶人昵称（仅实名捞瓶时返回）
 * @param pickerAvatar       捞瓶人头像（仅实名捞瓶时返回）
 * @param isCollected        捞起人是否已收藏（仅 PICKED 状态有意义）
 * @param commentCount       评论数（含回复）
 */
@Schema(description = "漂流瓶")
public record DriftBottleVO(
        Long bottleId,
        String content,
        Long wishId,
        String wishTitle,
        List<String> wishTags,
        String status,
        String role,
        LocalDateTime thrownAt,
        LocalDateTime pickedAt,
        Boolean isAnonymous,
        Long throwerUserId,
        String throwerNickname,
        String throwerAvatar,
        Boolean pickerIsAnonymous,
        Long pickerUserId,
        String pickerNickname,
        String pickerAvatar,
        Boolean isCollected,
        Long commentCount
) {
}
