package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.CouponSearchRequest;
import com.cloudmart.admin.dto.feign.CouponTemplateDTO;
import com.cloudmart.admin.dto.feign.CreateCouponTemplateRequest;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class CouponFeignClientFallbackFactory implements FallbackFactory<CouponFeignClient> {

    @Override
    public CouponFeignClient create(Throwable cause) {
        log.error("优惠券服务调用失败: {}", cause.getMessage());
        return new CouponFeignClient() {
            @Override
            public ApiResponse<List<CouponTemplateDTO>> listTemplates(CouponSearchRequest request) {
                throw new BusinessException("COUPON_SERVICE_UNAVAILABLE", "优惠券服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<CouponTemplateDTO> getTemplateById(Long id) {
                throw new BusinessException("COUPON_SERVICE_UNAVAILABLE", "优惠券服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<CouponTemplateDTO> createTemplate(CreateCouponTemplateRequest request) {
                throw new BusinessException("COUPON_SERVICE_UNAVAILABLE", "优惠券服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateTemplate(Long id, Map<String, Object> body) {
                throw new BusinessException("COUPON_SERVICE_UNAVAILABLE", "优惠券服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<CouponTemplateDTO> disableTemplate(Long id) {
                throw new BusinessException("COUPON_SERVICE_UNAVAILABLE", "优惠券服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<CouponTemplateDTO> enableTemplate(Long id) {
                throw new BusinessException("COUPON_SERVICE_UNAVAILABLE", "优惠券服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteCoupon(Long id) {
                throw new BusinessException("COUPON_SERVICE_UNAVAILABLE", "优惠券服务不可用，请稍后重试");
            }
        };
    }
}
