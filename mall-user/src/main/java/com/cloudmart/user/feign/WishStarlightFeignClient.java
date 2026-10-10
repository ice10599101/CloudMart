package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * N-3 邀请裂变：双向星光奖励发放（mall-wish 内部端点）。
 * 鉴权走 SEC-01 服务令牌（outbound-scopes 配置见 mall-user yml；
 * mall-wish 入站发行方白名单含 mall-user——既有 account-erasure 同源信任）。
 */
@FeignClient(name = "mall-wish", contextId = "userInviteStarlightFeignClient")
public interface WishStarlightFeignClient {

    @PostMapping("/internal/starlight/earn")
    ApiResponse<Map<String, Object>> earn(@RequestParam("userId") Long userId,
                                          @RequestParam("amount") Integer amount,
                                          @RequestParam("refId") Long refId,
                                          @RequestParam("operationId") String operationId,
                                          @RequestParam("source") String source);
}
