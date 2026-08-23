package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.NotificationChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 通知偏好批量更新请求（文档 2.14：PUT /wish/my/notification-preferences）。
 *
 * @param updates 更新项列表（逐项 upsert）
 */
@Schema(description = "通知偏好批量更新请求")
public record NotificationPreferenceUpdateRequest(
        @Schema(description = "更新项列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "更新项列表不能为空")
        @Valid
        List<UpdateItem> updates
) {

    /**
     * 单项更新：类型 + 渠道 + 开关。
     */
    @Schema(description = "偏好更新项")
    public record UpdateItem(
            @Schema(description = "通知类型（13 类之一）", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "通知类型不能为空")
            String type,

            @Schema(description = "渠道", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "渠道不能为空")
            NotificationChannel channel,

            @Schema(description = "是否开启", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "开关值不能为空")
            Boolean enabled
    ) {
    }
}
