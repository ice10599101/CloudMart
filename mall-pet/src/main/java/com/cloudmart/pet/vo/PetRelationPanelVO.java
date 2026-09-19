package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 宠物关系面板（三期）：已建立 / 收到申请 / 我发起的申请 / 可申请候选 + 规则说明。
 */
@Schema(description = "宠物关系面板")
public record PetRelationPanelVO(
        @Schema(description = "已建立的关系") List<PetRelationVO> relations,
        @Schema(description = "收到的申请（待我确认）") List<PetRelationVO> incoming,
        @Schema(description = "我发起的申请（等待对方确认）") List<PetRelationVO> outgoing,
        @Schema(description = "可申请的候选宠物") List<PetRelationVO> candidates,
        @Schema(description = "各类型上限（中文名 → 上限）") List<TypeLimit> limits
) {
    /** 关系类型上限说明（前端只展示，不参与判断） */
    @Schema(description = "关系类型上限")
    public record TypeLimit(
            @Schema(description = "类型") String relType,
            @Schema(description = "中文名") String label,
            @Schema(description = "每只宠物上限") Integer max,
            @Schema(description = "我当前数量") Integer current,
            @Schema(description = "是否独占") Boolean exclusive
    ) {
    }
}
