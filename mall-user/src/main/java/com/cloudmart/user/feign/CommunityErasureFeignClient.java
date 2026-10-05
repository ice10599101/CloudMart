package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * mall-community 社区数据擦除端点（T06 注销编排）：帖子/评论软删+去标识化（幂等）。
 * 请求头由 ServiceTokenFeignInterceptor 自动签名（iss=mall-user，scope=community:erasure）。
 */
@FeignClient(name = "mall-community", contextId = "userCommunityErasureFeignClient")
public interface CommunityErasureFeignClient {

    /** 幂等擦除该用户社区内容；恒返回 true（软删语义） */
    @PostMapping("/internal/account-erasure")
    ApiResponse<Boolean> erase(@RequestParam("userId") Long userId);
}
