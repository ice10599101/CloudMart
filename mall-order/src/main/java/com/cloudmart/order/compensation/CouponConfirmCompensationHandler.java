package com.cloudmart.order.compensation;

import com.cloudmart.common.async.compensation.CompensationHandler;
import com.cloudmart.order.feign.CouponFeignClient;
import com.cloudmart.order.feign.CouponFeignClient.UseCouponRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * T06：优惠券核销确认补偿处理器——支付成功时 confirm 失败的持久化重试。
 * 载荷：{"orderId":x,"userCouponId":y}；confirm 幂等（同订单重复确认安全）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponConfirmCompensationHandler implements CompensationHandler {

    private final CouponFeignClient couponFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public String action() {
        return "coupon-confirm";
    }

    @Override
    public void execute(String aggregateId, String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            couponFeignClient.confirmCoupon(new UseCouponRequest(
                    node.get("userCouponId").asLong(), node.get("orderId").asLong()));
        } catch (Exception e) {
            throw new IllegalStateException("coupon-confirm failed for order " + aggregateId + ": " + e.getMessage(), e);
        }
    }
}
