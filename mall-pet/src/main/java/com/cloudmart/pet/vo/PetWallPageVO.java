package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 留言墙分页（三期）：房间信息 + 一级留言（含主人回复）。 */
@Schema(description = "留言墙分页")
public record PetWallPageVO(
        @Schema(description = "墙所属宠物 ID") Long petId,
        @Schema(description = "宠物名") String petName,
        @Schema(description = "宠物种类") String species,
        @Schema(description = "宠物等级") Integer level,
        @Schema(description = "宠物进化阶段") Integer evolutionStage,
        @Schema(description = "宠物皮肤编码") String skinCode,
        @Schema(description = "主人昵称") String ownerNickname,
        @Schema(description = "房间欢迎语（未开放时为空）") String welcomeMessage,
        @Schema(description = "家园是否开放") Boolean roomPublic,
        @Schema(description = "当前页码（1 起）") Integer page,
        @Schema(description = "每页条数") Integer size,
        @Schema(description = "留言总数") Long total,
        @Schema(description = "我愿意留言的次数上限（每日）") Integer dailyPostLimit,
        @Schema(description = "留言列表") List<PetWallMessageVO> messages
) {
}
