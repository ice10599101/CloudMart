package com.cloudmart.pet.dto;

import java.time.LocalDateTime;

/**
 * 兑换码权威事实（R01）：mall-coupon /internal/exchange-codes/{code}/fact 的响应契约。
 * 强类型 DTO——状态与归属以 coupon 表为准，本端只读不信任客户端转述。
 */
public record ExchangeCodeFactDTO(
        Long codeId,
        String code,
        String status,
        Long userId,
        LocalDateTime exchangedAt,
        Long templateId
) {
}
