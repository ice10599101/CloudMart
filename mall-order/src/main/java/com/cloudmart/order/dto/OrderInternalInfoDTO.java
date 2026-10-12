package com.cloudmart.order.dto;

/**
 * 订单最小内部信息（SEC-04/R11）：供 mall-payment / mall-wms 做对象归属校验——
 * 只暴露归属与状态，不携带收件人、金额明细等业务数据。
 *
 * @param orderId 订单 ID
 * @param userId  订单归属用户
 * @param status  当前订单状态
 * @param zeroPay R11：零元内部结算单（无渠道支付凭证，对账跳过支付凭证核对）
 */
public record OrderInternalInfoDTO(Long orderId, Long userId, String status,
                                   java.math.BigDecimal payAmount, boolean zeroPay) {
    /** 兼容 4 参构造（默认非零元单） */
    public OrderInternalInfoDTO(Long orderId, Long userId, String status,
                                java.math.BigDecimal payAmount) {
        this(orderId, userId, status, payAmount, false);
    }
}
