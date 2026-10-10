package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 访客日志条目（§6 访客日志：家园页"今日访客"）。
 *
 * @param visitorUserId   访客用户 ID
 * @param visitorNickname 访客昵称（展示型数据 fail-open 占位）
 * @param visitorPetId    访客宠物 ID
 * @param visitorPetName  访客宠物名
 * @param source          拜访来源（NEIGHBOR/FRIEND/HOME）
 * @param createdAt       拜访时间
 */
@Schema(description = "今日访客条目")
public record VisitorLogVO(
        @Schema(description = "访客用户 ID") Long visitorUserId,
        @Schema(description = "访客昵称") String visitorNickname,
        @Schema(description = "访客宠物 ID") Long visitorPetId,
        @Schema(description = "访客宠物名") String visitorPetName,
        @Schema(description = "拜访来源") String source,
        @Schema(description = "拜访时间") LocalDateTime createdAt
) {
}
