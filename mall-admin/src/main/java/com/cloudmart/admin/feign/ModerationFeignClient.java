package com.cloudmart.admin.feign;

import com.cloudmart.admin.config.WishServiceTokenConfig;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * mall-wish 治理工单管理端 Feign 客户端（N01）。
 *
 * <p>X-Service-Token 由 {@link WishServiceTokenConfig} 签发注入；操作者 ID
 * 由 mall-wish 端从透传的 X-User-Id 头读取。</p>
 */
@FeignClient(name = "mall-wish", contextId = "moderationFeignClient",
        configuration = WishServiceTokenConfig.class,
        fallbackFactory = ModerationFeignClientFallbackFactory.class)
public interface ModerationFeignClient {

    /** 治理队列（按状态筛选 cursor 分页） */
    @GetMapping("/admin/moderation/cases")
    ApiResponse<List<Map<String, Object>>> listCases(@RequestParam("status") String status,
                                                     @RequestParam("cursor") Long cursor,
                                                     @RequestParam("pageSize") Integer pageSize);

    /** 作出治理决定 */
    @PostMapping("/admin/moderation/cases/{id}/decisions")
    ApiResponse<Long> decide(@PathVariable("id") Long caseId,
                             @RequestBody Map<String, Object> body,
                             @org.springframework.web.bind.annotation.RequestHeader(
                                     "X-User-Id") Long actorId);

    /** 申诉复核 */
    @PostMapping("/admin/moderation/appeals/{id}/decisions")
    ApiResponse<Void> resolveAppeal(@PathVariable("id") Long appealId,
                                    @RequestBody Map<String, Object> body,
                                    @org.springframework.web.bind.annotation.RequestHeader(
                                            "X-User-Id") Long reviewerId);
}
