package com.cloudmart.wms.dto;

/**
 * 订单内部信息（SEC-04/T21）：mall-order /internal/orders/{id} 回包。
 * 既用于用户查询物流前的对象归属校验，也用于出库建包的权威快照——
 * 收件信息以订单快照为准（可空，订单服务未返回时回退请求兜底）。
 */
public record OrderInternalInfoDTO(Long orderId, Long userId, String status,
                                   String receiverName, String receiverPhone, String receiverAddress) {
    /** 兼容 3 参构造（归属校验场景，无收件快照） */
    public OrderInternalInfoDTO(Long orderId, Long userId, String status) {
        this(orderId, userId, status, null, null, null);
    }
}
