package com.cloudmart.payment.dto;

/**
 * 订单最小内部信息（SEC-04/R11）：mall-order /internal/orders/{id} 回包，
 * 用于支付创建/查询前的对象归属校验与对账。
 *
 * @param zeroPay R11：零元内部结算单（无渠道支付凭证；对账反向扫描跳过）
 */
public record OrderInternalInfoDTO(Long orderId, Long userId, String status,
                                   java.math.BigDecimal payAmount, boolean zeroPay) {
    /** 兼容旧 4 参反序列化（R11 前的订单服务实例可能不带 zeroPay 字段） */
    public OrderInternalInfoDTO(Long orderId, Long userId, String status,
                                java.math.BigDecimal payAmount) {
        this(orderId, userId, status, payAmount, false);
    }
}
