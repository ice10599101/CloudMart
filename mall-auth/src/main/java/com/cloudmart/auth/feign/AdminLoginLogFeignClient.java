package com.cloudmart.auth.feign;

import com.cloudmart.auth.dto.LoginLogRecordRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "mall-admin",
        contextId = "loginLogClient",
        path = "/logs/login",
        fallbackFactory = AdminLoginLogFeignClientFallbackFactory.class
)
public interface AdminLoginLogFeignClient {

    @PostMapping("/record")
    ApiResponse<Void> recordLogin(@RequestBody LoginLogRecordRequest request);
}
