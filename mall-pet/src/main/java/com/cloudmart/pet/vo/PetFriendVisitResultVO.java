package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 好友互访结果（三期）：房间快照 + 好友层收益（访问次数、关系亲密度）。 */
@Schema(description = "好友互访结果")
public record PetFriendVisitResultVO(
        @Schema(description = "房间访问快照（含本次收益）") PetRoomVisitVO room,
        @Schema(description = "好友昵称") String nickname,
        @Schema(description = "我访问该好友的累计次数") Integer visitCount,
        @Schema(description = "是否给双方关系加了亲密度") Boolean relationIntimacyAdded,
        @Schema(description = "结果文案") String message
) {
}
