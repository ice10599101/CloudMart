package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.dto.SeckillQuoteDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * mall-seckill 内部报价回查客户端（T09）：秒杀订单以 mall-seckill 冻结快照
 * 为价格权威——订单引用活动报价，秒杀价与普通价不混用；服务令牌自动签名。
 */
@FeignClient(contextId = "seckillFeignClient", name = "mall-seckill",
        fallbackFactory = SeckillFeignClientFallbackFactory.class)
public interface SeckillFeignClient {

    @GetMapping("/internal/seckill/requests/{requestId}")
    ApiResponse<SeckillQuoteDTO> getSeckillQuote(@PathVariable("requestId") String requestId);
}
