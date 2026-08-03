package com.cloudmart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "创建秒杀活动请求")
public record CreateActivityRequest(

    @NotBlank @Size(max = 100) @Schema(description = "活动名称")
    String name,

    @Size(max = 500) @Schema(description = "活动描述")
    String description,

    @NotNull @Schema(description = "开始时间")
    LocalDateTime startTime,

    @NotNull @Schema(description = "结束时间")
    LocalDateTime endTime
) {}
