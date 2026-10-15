package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * mall-software 管理端 Feign 客户端（S04 审核决策；S09 扩展资格/授权/举报等）。
 *
 * <p>下游 /admin/software/** 由 @PreAuthorize("hasRole('INTERNAL')") 保护；
 * 出站令牌经全局 ServiceTokenFeignInterceptor 签名（iss=mall-admin、
 * scope=software:admin），操作管理员 X-User-Id 由 AdminFeignInterceptor 注入，
 * 裁决管理员身份不在请求体中传递。</p>
 */
@FeignClient(contextId = "softwareFeignClient", name = "mall-software",
        path = "/admin/software", fallbackFactory = SoftwareFeignClientFallbackFactory.class)
public interface SoftwareFeignClient {

    /** 审核决策：携带 revisionId+submitSeq+expectedVersion 防旧批次覆盖 */
    @PostMapping("/revisions/{revisionId}/decision")
    ApiResponse<Object> decision(@PathVariable("revisionId") Long revisionId,
                                 @RequestBody Map<String, Object> body);
}
