package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** T15：邀请台账（mall-user 内部端点代理；服务令牌 INTERNAL） */
@FeignClient(contextId = "inviteFeignClient", name = "mall-user", path = "/internal/invites",
        fallbackFactory = InviteFeignClientFallbackFactory.class)
public interface InviteFeignClient {

    @GetMapping("/relations")
    ApiResponse<Object> relations(@org.springframework.web.bind.annotation.RequestParam(
            value = "rewardStatus", required = false) String rewardStatus,
            @org.springframework.web.bind.annotation.RequestParam("page") int page,
            @org.springframework.web.bind.annotation.RequestParam("size") int size);

    @PostMapping("/relations/{relationId}/release-hold")
    ApiResponse<Object> releaseHold(@PathVariable("relationId") Long relationId,
                                    @RequestBody Map<String, String> body);
}
