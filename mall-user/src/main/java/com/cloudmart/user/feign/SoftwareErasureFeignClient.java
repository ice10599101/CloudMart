package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * mall-software 软件域擦除端点（方案 11.3 注销编排 SOFTWARE/ERASE 步骤）：
 * 下架软件、匿名化评论、清理关系与授权身份；幂等可续跑，未完成返回可重试失败。
 * 请求头由 ServiceTokenFeignInterceptor 自动签名（iss=mall-user，scope=software:erasure）。
 */
@FeignClient(name = "mall-software", contextId = "userSoftwareErasureFeignClient")
public interface SoftwareErasureFeignClient {

    /** @return true=本域全部完成（DONE 后重复调用亦返回成功）；false/失败=未完成不得推进文件擦除 */
    @PostMapping("/internal/account-erasure")
    ApiResponse<Boolean> erase(@RequestParam("userId") Long userId);
}
