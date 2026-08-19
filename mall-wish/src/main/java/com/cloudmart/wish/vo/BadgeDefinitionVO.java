package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 徽章图鉴条目 VO（文档 2.9 GET /wish/badges/definitions，公开接口）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "徽章定义（图鉴条目）")
public class BadgeDefinitionVO {

    @Schema(description = "徽章 ID", example = "2001")
    private Long badgeId;

    @Schema(description = "徽章编码（唯一）", example = "FIRST_WISH")
    private String code;

    @Schema(description = "徽章名称", example = "第一次许愿")
    private String name;

    @Schema(description = "徽章图标 URL（空串为占位，待运营配置）")
    private String icon;

    @Schema(description = "获取方式描述", example = "发布第一个心愿")
    private String description;

    @Schema(description = "稀有度: COMMON/RARE/EPIC/LEGENDARY", example = "COMMON")
    private String rarity;

    @Schema(description = "触发条件声明（前端展示如何获取）")
    private ConditionVO condition;

    /**
     * 触发条件展示结构。
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "徽章触发条件")
    public static class ConditionVO {

        @Schema(description = "条件类型", example = "WISH_CREATED")
        private String type;

        @Schema(description = "达标阈值", example = "1")
        private Integer threshold;

        @Schema(description = "获取方式描述", example = "发布第一个心愿")
        private String description;
    }
}
