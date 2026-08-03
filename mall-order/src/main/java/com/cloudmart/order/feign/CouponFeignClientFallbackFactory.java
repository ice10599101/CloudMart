package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.feign.CouponFeignClient.ReturnCouponRequest;
import com.cloudmart.order.feign.CouponFeignClient.UseCouponRequest;
import com.cloudmart.order.feign.CouponFeignClient.UserCouponDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CouponFeignClientFallbackFactory implements FallbackFactory<CouponFeignClient> {

    @Override
    public CouponFeignClient create(Throwable cause) {
        log.error("优惠券服务调用失败: {}", cause.getMessage());
        return new CouponFeignClient() {
            @Override
            public ApiResponse<Void> useCoupon(UseCouponRequest request) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "COUPON_SERVICE_UNAVAILABLE", "优惠券服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> returnCoupon(ReturnCouponRequest request) {
                log.warn("退回优惠券降级跳过, userCouponId={}: {}", request.userCouponId(), cause.getMessage());
                return ApiResponse.ok(null);
            }

            @Override
            public ApiResponse<UserCouponDTO> getCouponById(Long id) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "COUPON_SERVICE_UNAVAILABLE", "优惠券服务不可用，请稍后重试");
            }
        };
    }
}
