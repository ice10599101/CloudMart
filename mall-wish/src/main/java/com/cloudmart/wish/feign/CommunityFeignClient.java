package com.cloudmart.wish.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Map;

/**
 * mall-community Feign 客户端（Sprint 2.7 还愿内容流转专用）。
 *
 * <p>走 mall-community 内部端点 /internal/content/flow/**（X-Internal-Call
 * 认证由 Feign 拦截器统一注入）。调用失败降级为抛出/返回失败——
 * 内容流转为增强功能，失败记录日志由管理端重试，不阻断还愿主链路。</p>
 */
@FeignClient(name = "mall-community", contextId = "wishCommunityFeignClient", fallbackFactory = CommunityFeignClientFallbackFactory.class)
public interface CommunityFeignClient {

    /**
     * 生成传承帖子（《我的梦想实现记录》图文模板）。
     *
     * @param body {userId, title, content, coverImage?, mediaUrls?, tagNames?}
     * @return ApiResponse 包含 {postId}
     */
    @PostMapping("/internal/content/flow/posts")
    ApiResponse<Map<String, Object>> createLegacyPost(@RequestBody Map<String, Object> body);

    /**
     * 隐藏传承帖子（状态同步：还愿故事删除 → 帖子隐藏）。
     */
    @PutMapping("/internal/content/flow/posts/{id}/hide")
    ApiResponse<Void> hideLegacyPost(@PathVariable("id") Long postId);
}
