package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.NotificationChannel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 用户通知偏好矩阵 VO（文档 2.14：GET /wish/my/notification-preferences）。
 *
 * @param preferences 13 类通知 × 4 渠道开关（无记录项为默认开启 true）
 */
@Schema(description = "用户通知偏好矩阵")
public record NotificationPreferenceMatrixVO(
        @Schema(description = "偏好列表") List<PreferenceItemVO> preferences
) {

    /**
     * 单类型偏好：4 渠道开关。
     */
    @Schema(description = "单类型偏好")
    public record PreferenceItemVO(
            @Schema(description = "通知类型") String type,
            @Schema(description = "各渠道开关") Map<NotificationChannel, Boolean> channels
    ) {
    }
}
