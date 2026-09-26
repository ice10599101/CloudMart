package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * mall-user 用户信息客户端（修复：此前误经 mall-wish 的 /users/batch 转查，
 * mall-wish 并无该端点，昵称解析一直走降级占位）。
 *
 * <p>展示型数据 Fail-Open：失败返回空列表，调用方使用占位昵称。</p>
 */
@FeignClient(name = "mall-user", contextId = "petUserFeignClient",
        fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    /** 批量用户信息（对战对手主人昵称/头像；字段取子集） */
    @GetMapping("/users/batch")
    ApiResponse<List<Map<String, Object>>> batchGetUsers(@RequestParam("ids") List<Long> ids);
}
