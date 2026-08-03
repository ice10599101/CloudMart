package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "加入拼团请求")
public record JoinGroupRequest(
    @Schema(description = "拼团组ID，为空则开新团") Long groupOrderId,
    @Schema(description = "拼团活动ID") @NotNull Long activityId
) {}
