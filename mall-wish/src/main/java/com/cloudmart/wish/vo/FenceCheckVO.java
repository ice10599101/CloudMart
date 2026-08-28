package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 围栏打卡结果（Sprint 3.1/3.2 契约：仅返回结果 + 触发的心愿信息，
 * 不回传围栏坐标——隐私验收：API 响应无 center_lat/center_lng 字段）。
 */
@Schema(description = "围栏打卡结果")
public record FenceCheckVO(
        Long wishId,
        Boolean insideFence,
        String fenceName,
        Boolean bloomTriggered,
        Integer matchedCount
) {
}
