package com.cloudmart.product.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * N-2 fail-open：评价激励属促销性质，mall-wish 不可用时评价主流程不受阻——
 * 记 warn 日志，operationId=REVIEW_REWARD:{reviewId} 幂等键保留（如需补发可安全重放）。
 */
@Slf4j
@Component
public class WishStarlightFeignClientFallbackFactory implements FallbackFactory<WishStarlightFeignClient> {

    @Override
    public WishStarlightFeignClient create(Throwable cause) {
        return (userId, amount, refId, operationId, source) -> {
            log.warn("N-2 评价返星光发放失败（fail-open 跳过）: userId={}, refId={}, operationId={}, cause={}",
                    userId, refId, operationId, cause.getMessage());
            return ApiResponse.ok(Map.of("creditedAmount", 0, "status", "SKIPPED"));
        };
    }
}
