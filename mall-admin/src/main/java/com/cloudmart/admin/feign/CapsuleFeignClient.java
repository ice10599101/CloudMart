package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.admin.config.WishServiceTokenConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * mall-wish 时间胶囊管理端 Feign 客户端（Sprint 2.4 + R17 公共墙审核）。
 *
 * <p>下游 /admin/capsules/** 由 @PreAuthorize("hasRole('INTERNAL')") 保护，
 * X-Internal-Call/X-User-Id 头由 AdminFeignInterceptor 全局注入，
 * 裁决管理员身份不在请求体中传递。</p>
 */
@FeignClient(contextId = "capsuleFeignClient", name = "mall-wish",
        configuration = WishServiceTokenConfig.class, path = "/admin/capsules",
        fallbackFactory = CapsuleFeignClientFallbackFactory.class)
public interface CapsuleFeignClient {

    @GetMapping("/stats")
    ApiResponse<Object> getStats();

    /** R17 待审核队列（wallStatus=1） */
    @GetMapping("/wall/pending")
    ApiResponse<Object> wallPending(@RequestParam("page") int page, @RequestParam("size") int size);

    /** R17 已上墙列表（wallStatus=2，供下架管理） */
    @GetMapping("/wall/approved")
    ApiResponse<Object> wallApproved(@RequestParam("page") int page, @RequestParam("size") int size);

    /** R17 审核：{approve:true/false, reason:"驳回理由"} */
    @PostMapping("/wall/{id}/decide")
    ApiResponse<Object> decideWall(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    /** R17 下架：{reason:"下架理由"} */
    @PostMapping("/wall/{id}/take-down")
    ApiResponse<Object> takeDownWall(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);
}
