package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import com.cloudmart.order.dto.TieredMatchRequest;
import com.cloudmart.order.dto.TieredMatchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 营销内部调用降级（T10/R12）：全部 fail-closed——
 * 拼团快照查不到不能按普通价建单；满减匹配不可达时不能漏算优惠导致少收款/前后不一致。
 */
@Slf4j
@Component
public class MarketingFeignClientFallbackFactory implements FallbackFactory<MarketingFeignClient> {

    @Override
    public MarketingFeignClient create(Throwable cause) {
        log.warn("[T10/R12] 营销内部服务不可用: {}", cause.getMessage());
        return new MarketingFeignClient() {
            @Override
            public ApiResponse<com.cloudmart.order.dto.GroupQuoteDTO> getGroupQuote(Long groupOrderId) {
                throw FeignBusinessErrors.parse(cause, "GROUP_QUOTE_UNAVAILABLE", "拼团快照服务暂不可用，请稍后重试");
            }

            @Override
            public ApiResponse<TieredMatchResult> matchTiered(TieredMatchRequest request) {
                throw new BusinessException("TIERED_PROMOTION_UNAVAILABLE", "优惠活动服务暂不可用，无法生成报价");
            }
        };
    }
}
