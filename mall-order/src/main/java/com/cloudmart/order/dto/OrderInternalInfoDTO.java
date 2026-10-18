package com.cloudmart.order.dto;

/**
 * 订单内部信息（SEC-04/R11）：供 mall-payment / mall-wms 做对象归属与出库快照。
 *
 * <p>T21：出库建档需要权威收件快照（WMS 不信任请求传入的收件信息），故在原有
 * 归属/状态最小字段上扩展 receiverName/receiverPhone/receiverAddress（可空）。
 * 金额与其它明细仍不暴露。</p>
 *
 * @param orderId         订单 ID
 * @param userId          订单归属用户
 * @param status          当前订单状态
 * @param payAmount       实付金额
 * @param zeroPay         R11：零元内部结算单（无渠道支付凭证，对账跳过支付凭证核对）
 * @param receiverName    收件人姓名（出库快照；可空）
 * @param receiverPhone   收件人电话（出库快照；可空）
 * @param receiverAddress 收件人地址（出库快照；可空）
 */
public record OrderInternalInfoDTO(Long orderId, Long userId, String status,
                                   java.math.BigDecimal payAmount, boolean zeroPay,
                                   String receiverName, String receiverPhone, String receiverAddress) {
    /** 兼容 4 参构造（默认非零元单、无收件快照） */
    public OrderInternalInfoDTO(Long orderId, Long userId, String status,
                                java.math.BigDecimal payAmount) {
        this(orderId, userId, status, payAmount, false, null, null, null);
    }
}
