package com.cloudmart.order.feign;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 商品查询降级（TRADE-01）：报价依赖权威价格——商品服务不可用时必须拒绝报价，
 * 绝不能按客户端提交的价格生成订单（fail-closed）。
 */
@Slf4j
@Component
public class ProductFeignClientFallbackFactory implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        log.error("商品服务调用失败: {}", cause.getMessage());
        return ids -> {
            throw FeignBusinessErrors.parse(cause, "PRODUCT_SERVICE_UNAVAILABLE", "商品服务暂不可用，无法生成报价");
        };
    }
}
