package com.cloudmart.payment.dto;

/**
 * 订单最小内部信息（SEC-04）：mall-order /internal/orders/{id} 回包，
 * 用于支付创建/查询前的对象归属校验。
 */
public record OrderInternalInfoDTO(Long orderId, Long userId, String status,
                                   java.math.BigDecimal payAmount) {
}
