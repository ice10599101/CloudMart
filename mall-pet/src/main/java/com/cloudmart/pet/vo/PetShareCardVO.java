package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 宠物动态分享卡片（原文档 §36：宠物动态卡片，用户可发布到社区）。
 * 文案由服务端生成；前端复制后跳转发帖页携带。
 */
@Schema(description = "宠物动态分享卡片")
public record PetShareCardVO(
        @Schema(description = "卡片类型: LEVEL_UP/ACHIEVEMENT/BOTTLE/BATTLE/DAILY") String type,
        String title,
        String content,
        @Schema(description = "附加数据（如成就名/捞瓶总数/胜率）") String highlight
) {
}
