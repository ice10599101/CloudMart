package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 漂流瓶（匿名化）：匿名投瓶时不透出投瓶人身份；实名投瓶时透出投瓶人昵称/头像/ID。
 *
 * @param bottleId         漂流瓶 ID
 * @param content          自由匿名文字（富文本 HTML；关联心愿时为空）
 * @param wishId           关联心愿 ID（自由文字时为 null）
 * @param wishTitle        关联心愿标题（自由文字时为 null）
 * @param wishTags         关联心愿标签
 * @param status           FLOATING / PICKED
 * @param role             THROWN（我投出的）/ PICKED（我捞到的）
 * @param thrownAt         投瓶时间
 * @param pickedAt         捞起时间（未捞起为 null）
 * @param isAnonymous      投瓶是否匿名（对外始终返回；匿名时不返回 thrower 信息）
 * @param throwerUserId    投瓶人用户 ID（仅实名投瓶时返回，匿名为 null）
 * @param throwerNickname  投瓶人昵称（仅实名投瓶时返回）
 * @param throwerAvatar    投瓶人头像（仅实名投瓶时返回）
 * @param commentCount     评论数（含回复）
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
        Long commentCount
) {
}