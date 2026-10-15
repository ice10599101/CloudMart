package com.cloudmart.admin.feign;

import com.cloudmart.admin.config.SoftwareFeignConfiguration;
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
 * mall-software 管理端 Feign 客户端（S09 全量）：
 * 审核/评论管理/发布资格/付款授权/联系入口/举报/失效反馈。
 * 出站令牌经全局 ServiceTokenFeignInterceptor 签名（scope=software:admin），
 * 操作管理员 X-User-Id 由 AdminFeignInterceptor 注入。
 */
@FeignClient(contextId = "softwareFeignClient", name = "mall-software",
        path = "/admin/software", fallbackFactory = SoftwareFeignClientFallbackFactory.class,
        configuration = SoftwareFeignConfiguration.class)
public interface SoftwareFeignClient {

    /** 审核决策（S04）：携带 revisionId+submitSeq+expectedVersion 防旧批次覆盖 */
    @PostMapping("/revisions/{revisionId}/decision")
    ApiResponse<Object> decision(@PathVariable("revisionId") Long revisionId,
                                 @RequestBody Map<String, Object> body);

    /** 来源状态处置（S05）：核实/禁用/解除 */
    @PostMapping("/{softwareId}/sources/state")
    ApiResponse<Object> sourceState(@PathVariable("softwareId") Long softwareId,
                                    @RequestBody Map<String, Object> body);

    @GetMapping("/revisions/pending")
    ApiResponse<Object> pending(@RequestParam("page") int page, @RequestParam("size") int size);

    @GetMapping("/{softwareId}/admin-detail")
    ApiResponse<Object> adminDetail(@PathVariable("softwareId") Long softwareId);

    @GetMapping("/{softwareId}/comments")
    ApiResponse<Object> comments(@PathVariable("softwareId") Long softwareId,
                                 @RequestParam(value = "status", required = false) String status,
                                 @RequestParam("page") int page, @RequestParam("size") int size);

    @PostMapping("/{softwareId}/comments/{commentId}/moderate")
    ApiResponse<Object> moderateComment(@PathVariable("softwareId") Long softwareId,
                                        @PathVariable("commentId") Long commentId,
                                        @RequestBody Map<String, Object> body);

    @GetMapping("/publisher-permissions")
    ApiResponse<Object> permissions(@RequestParam("page") int page, @RequestParam("size") int size);

    @PostMapping("/publisher-permissions/batch")
    ApiResponse<Object> permissionsOf(@RequestBody List<Long> userIds);

    @PostMapping("/publisher-permissions/{userId}/toggle")
    ApiResponse<Object> togglePermission(@PathVariable("userId") Long userId,
                                         @RequestBody Map<String, Object> body);

    @GetMapping("/{softwareId}/access-grants")
    ApiResponse<Object> grants(@PathVariable("softwareId") Long softwareId);

    @PostMapping("/{softwareId}/access-grants")
    ApiResponse<Object> grant(@PathVariable("softwareId") Long softwareId,
                              @RequestBody Map<String, Object> body);

    @PostMapping("/{softwareId}/access-grants/{userId}/revoke")
    ApiResponse<Object> revokeGrant(@PathVariable("softwareId") Long softwareId,
                                    @PathVariable("userId") Long userId,
                                    @RequestBody Map<String, Object> body);

    @GetMapping("/access-contacts")
    ApiResponse<Object> contacts();

    @PostMapping("/access-contacts")
    ApiResponse<Object> saveContact(@RequestBody Map<String, Object> body);

    @GetMapping("/reports")
    ApiResponse<Object> reports(@RequestParam(value = "status", required = false) String status,
                                @RequestParam("page") int page, @RequestParam("size") int size);

    @PostMapping("/reports/{reportId}/handle")
    ApiResponse<Object> handleReport(@PathVariable("reportId") Long reportId,
                                     @RequestBody Map<String, Object> body);

    @GetMapping("/link-feedbacks")
    ApiResponse<Object> linkFeedbacks(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("page") int page, @RequestParam("size") int size);

    @PostMapping("/link-feedbacks/{feedbackId}/mark")
    ApiResponse<Object> markFeedback(@PathVariable("feedbackId") Long feedbackId,
                                     @RequestBody Map<String, Object> body);
}
