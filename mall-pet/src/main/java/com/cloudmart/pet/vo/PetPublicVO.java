package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 他人主页公开宠物卡片（隐私开关关闭时 404 PET_NOT_PUBLIC）。
 */
@Schema(description = "公开宠物卡片")
public record PetPublicVO(
        Long petId,
        String name,
        String species,
        Integer level,
        String growthStage,
        String personality,
        Integer achievementCount,
        Long ownerUserId
) {
}
