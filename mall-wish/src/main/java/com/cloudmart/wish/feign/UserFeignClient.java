package com.cloudmart.wish.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * mall-user Feign 客户端。
 *
 * <p>用于心愿宇宙模块获取用户基础信息（昵称、头像）以填充 VO 的作者信息字段。
 * 调用失败时降级为占位值（Fail Open），不阻塞心愿主链路。</p>
 *
 * <p>对应文档 1.2 节"用户体系复用 mall_user"。</p>
 */
@FeignClient(name = "mall-user", contextId = "wishUserFeignClient", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    /**
     * 获取单个用户信息。
     *
     * @param id 用户 ID
     * @return ApiResponse 包含 {id, nickname, avatar, ...} 的 Map
     */
    @GetMapping("/users/{id}")
    ApiResponse<Map<String, Object>> getUserById(@PathVariable("id") Long id);

    /**
     * 批量获取用户信息（避免 N+1，列表场景使用）。
     *
     * @param ids 用户 ID 列表（最多 100 个）
     * @return ApiResponse 包含用户信息 Map 列表
     */
    @GetMapping("/users/batch")
    ApiResponse<List<Map<String, Object>>> batchGetUsers(@RequestParam("ids") List<Long> ids);
}
