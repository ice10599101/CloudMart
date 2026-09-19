package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 好友面板（三期）：好友 + 收到申请 + 我发出的申请 + 今日互访余量。 */
@Schema(description = "宠物好友面板")
public record PetFriendPanelVO(
        @Schema(description = "好友列表") List<PetFriendVO> friends,
        @Schema(description = "收到的申请") List<PetFriendVO> incoming,
        @Schema(description = "我发出的申请") List<PetFriendVO> outgoing,
        @Schema(description = "好友上限") Integer maxFriends,
        @Schema(description = "今日互访次数上限") Integer dailyVisitLimit,
        @Schema(description = "今日已互访次数") Integer todayVisitCount,
        @Schema(description = "今日剩余互访次数") Integer remainingVisits
) {
}
