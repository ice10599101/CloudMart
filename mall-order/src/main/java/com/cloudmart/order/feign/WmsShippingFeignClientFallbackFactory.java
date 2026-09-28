package com.cloudmart.order.feign;

import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 物流包裹降级（WMS-01 闭环）：发货必须落到真实包裹——WMS 不可用时
 * 管理端发货失败（fail-closed），绝不出现无包裹记录的 SHIPPED。
 */
@Slf4j
@Component
public class WmsShippingFeignClientFallbackFactory implements FallbackFactory<WmsShippingFeignClient> {

    @Override
    public WmsShippingFeignClient create(Throwable cause) {
        log.error("mall-wms 物流调用失败: {}", cause.getMessage());
        return new WmsShippingFeignClient() {
            @Override
            public com.cloudmart.common.api.ApiResponse<Map<String, Object>> createShipping(
                    Map<String, Object> request) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "物流服务暂不可用，发货失败");
            }

            @Override
            public com.cloudmart.common.api.ApiResponse<Map<String, Object>> updateStatus(
                    Long id, String status) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "物流服务暂不可用，出库失败");
            }
        };
    }
}
