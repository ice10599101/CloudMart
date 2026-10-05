package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * mall-file 文件资产处置端点（T06 注销编排）：未引用删除/被引用匿名化（幂等）。
 * 请求头由 ServiceTokenFeignInterceptor 自动签名（iss=mall-user，scope=file:erasure）。
 */
@FeignClient(name = "mall-file", contextId = "userFileErasureFeignClient")
public interface FileErasureFeignClient {

    /** 幂等处置该用户文件资产；恒返回 true */
    @PostMapping("/internal/account-erasure")
    ApiResponse<Boolean> erase(@RequestParam("userId") Long userId);
}
