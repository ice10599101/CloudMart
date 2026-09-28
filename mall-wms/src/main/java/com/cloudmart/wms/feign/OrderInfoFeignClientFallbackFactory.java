package com.cloudmart.wms.feign;

import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 订单信息查询降级（SEC-04）：归属校验依赖权威订单数据——读不到时必须拒绝，
 * 绝不能把"查不到归属"当作放行依据（fail-closed）。
 */
@Slf4j
@Component
public class OrderInfoFeignClientFallbackFactory implements FallbackFactory<OrderInfoFeignClient> {

    @Override
    public OrderInfoFeignClient create(Throwable cause) {
        log.error("mall-order 订单信息查询失败: {}", cause.getMessage());
        return orderId -> {
            throw new BusinessException("ORDER_INFO_UNAVAILABLE", "订单服务暂不可用，无法校验物流归属");
        };
    }
}
