package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 传承发起结果（Sprint 2.7，文档 2.8 契约：POST /wishes/{id}/fulfillment/inherit）。
 */
@Schema(description = "传承推送结果")
public record InheritResultVO(
        Long inheritId,
        Integer pushedCount,
        LocalDateTime createdAt
) {
}
