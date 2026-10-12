package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.dto.GroupQuoteDTO;
import com.cloudmart.order.dto.TieredMatchRequest;
import com.cloudmart.order.dto.TieredMatchResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * mall-marketing 内部客户端（T10/R12）：
 * T10 拼团快照回查；R12 报价链路满减自动匹配。服务令牌自动签名，全部 fail-closed。
 */
@FeignClient(contextId = "marketingFeignClient", name = "mall-marketing",
        fallbackFactory = MarketingFeignClientFallbackFactory.class)
public interface MarketingFeignClient {

    @GetMapping("/internal/groups/{groupOrderId}")
    ApiResponse<GroupQuoteDTO> getGroupQuote(@PathVariable("groupOrderId") Long groupOrderId);

    /** R12：购物篮自动匹配最优满减（多活动重叠取优惠最大者，未命中 matched=false） */
    @PostMapping("/internal/tiered/match")
    ApiResponse<TieredMatchResult> matchTiered(@RequestBody TieredMatchRequest request);
}
