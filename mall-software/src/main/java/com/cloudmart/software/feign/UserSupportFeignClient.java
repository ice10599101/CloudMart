package com.cloudmart.software.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.dto.software.SoftwareProfileVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 用户最小资料客户端（方案 9.6）：出站令牌 scope=user:software-support。
 * 上限 100 个 ID；结果仅含存在的用户，缺失 ID 由调用方识别并降级展示。
 */
@FeignClient(name = "mall-user", contextId = "softwareUserSupportFeignClient",
        fallbackFactory = UserSupportFeignClientFallbackFactory.class)
public interface UserSupportFeignClient {

    @PostMapping("/internal/software-support/profiles")
    ApiResponse<List<SoftwareProfileVO>> profiles(@RequestBody Map<String, List<Long>> body);
}
