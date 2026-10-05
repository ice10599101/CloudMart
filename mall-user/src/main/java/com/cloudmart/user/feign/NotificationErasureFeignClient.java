package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * mall-notification 通知数据擦除端点（T06 注销编排）：通知/会话/消息物理删除（幂等）。
 * 请求头由 ServiceTokenFeignInterceptor 自动签名（iss=mall-user，scope=notification:erasure）。
 */
@FeignClient(name = "mall-notification", contextId = "userNotificationErasureFeignClient")
public interface NotificationErasureFeignClient {

    /** 幂等擦除该用户通知数据；恒返回 true */
    @PostMapping("/internal/account-erasure")
    ApiResponse<Boolean> erase(@RequestParam("userId") Long userId);
}
