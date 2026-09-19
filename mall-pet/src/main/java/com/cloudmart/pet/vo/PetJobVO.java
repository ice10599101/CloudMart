package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 打工岗位（pet_job_config 出参，参数服务端下发，客户端禁止硬编码）。
 */
@Schema(description = "打工岗位")
public record PetJobVO(
        Long configId,
        String name,
        String description,
        Integer durationSeconds,
        Integer energyCost,
        Integer hungerCost,
        Integer expReward,
        Integer currencyReward,
        Integer requiredLevel,
        @Schema(description = "当前宠物是否满足接单条件") Boolean eligible
) {
}
