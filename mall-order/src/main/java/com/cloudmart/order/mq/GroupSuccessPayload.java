package com.cloudmart.order.mq;

import java.util.List;

/**
 * GROUP_SUCCESS 成团事件载荷（mall-marketing GroupActivityServiceImpl 出账）。
 * 成员收货信息只认参团时冻结的 {@link Member} 快照（C20：不再回消费时默认地址）。
 */
public record GroupSuccessPayload(
        Long groupOrderId,
        Long activityId,
        Long productId,
        Long skuId,
        String groupPrice,
        List<Long> memberUserIds,
        List<Member> members
) {
    /** 成员参团地址快照（orderTaskStatus=ABSENT_ADDRESS 表示成员尚未补齐地址） */
    public record Member(
            Long userId,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            String orderTaskStatus
    ) {
    }
}
