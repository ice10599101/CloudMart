package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.dto.GroupQuoteDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * mall-marketing 内部拼团快照客户端（T10）：建成团订单前回查成团状态、
 * 成员归属与拼团价快照——拼团价与普通价不混用；服务令牌自动签名。
 */
@FeignClient(contextId = "marketingFeignClient", name = "mall-marketing",
        fallbackFactory = MarketingFeignClientFallbackFactory.class)
public interface MarketingFeignClient {

    @GetMapping("/internal/groups/{groupOrderId}")
    ApiResponse<GroupQuoteDTO> getGroupQuote(@PathVariable("groupOrderId") Long groupOrderId);
}
