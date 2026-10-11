package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import com.cloudmart.pet.dto.ExchangeCodeFactDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * mall-coupon 兑换码权威事实客户端（R01）降级：
 * 降级明确失败（抛业务异常），由核销操作台账决定 REJECTED 或 PENDING——
 * 绝不降级为"核验通过"，不跳过资格校验直接造权益。
 */
@Slf4j
@Component
public class CouponFeignClientFallbackFactory implements FallbackFactory<CouponFeignClient> {

    @Override
    public CouponFeignClient create(Throwable cause) {
        log.error("调用优惠券服务失败: {}", cause.getMessage());
        return code -> {
            throw FeignBusinessErrors.parse(cause, "COUPON_SERVICE_UNAVAILABLE", "优惠券服务暂不可用，核验稍后重试");
        };
    }
}
