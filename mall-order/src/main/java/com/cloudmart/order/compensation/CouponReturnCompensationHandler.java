package com.cloudmart.order.compensation;

import com.cloudmart.common.async.compensation.CompensationHandler;
import com.cloudmart.order.feign.CouponFeignClient.ReturnCouponRequest;
import com.cloudmart.order.feign.CouponFeignClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 退券补偿处理器（ASYNC-01）：取消/退款后退回优惠券失败的持久化重试。
 * 载荷：{"orderId":x,"couponId":y}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponReturnCompensationHandler implements CompensationHandler {

    private final CouponFeignClient couponFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public String action() {
        return "coupon-return";
    }

    @Override
    public void execute(String aggregateId, String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            couponFeignClient.returnCoupon(new ReturnCouponRequest(
                    node.get("couponId").asLong(), node.get("orderId").asLong()));
        } catch (Exception e) {
            throw new IllegalStateException("coupon-return failed for order " + aggregateId + ": " + e.getMessage(), e);
        }
    }
}
