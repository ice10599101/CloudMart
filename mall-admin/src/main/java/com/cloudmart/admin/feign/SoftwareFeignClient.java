package com.cloudmart.admin.feign;

import com.cloudmart.admin.config.SoftwareFeignConfiguration;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /** 审核决策（SW01）：定位不可变提交记录 revisionId，不接收批次/版本号 */
    @PostMapping("/revisions/{revisionId}/decision")
    ApiResponse<Object> decision(@PathVariable("revisionId") Long revisionId,
                                 @RequestBody Map<String, Object> body);

    /** 来源状态处置（S05）：核实/禁用/解除 */
    @PostMapping("/{softwareId}/sources/state")
    ApiResponse<Object> sourceState(@PathVariable("softwareId") Long softwareId,
                                    @RequestBody Map<String, Object> body);

    /** 来源清单（SW06）：当前线上快照按版本/平台/架构，链接已脱敏 */
    @GetMapping("/{softwareId}/sources")
    ApiResponse<Object> sources(@PathVariable("softwareId") Long softwareId);

    @GetMapping("/revisions/pending")
    ApiResponse<Object> pending(@RequestParam("page") int page, @RequestParam("size") int size);

    /** 审核快照对比（目标快照 + 当前线上快照） */
    @GetMapping("/revisions/{revisionId}")
    ApiResponse<Object> reviewDetail(@PathVariable("revisionId") Long revisionId);

    @GetMapping("/{softwareId}/admin-detail")
    ApiResponse<Object> adminDetail(@PathVariable("softwareId") Long softwareId);

    // ---------------- 目录与处置（software:list/moderate/feature） ----------------

    @GetMapping("/items")
    ApiResponse<Object> items(@RequestParam(value = "keyword", required = false) String keyword,
                              @RequestParam(value = "ownerId", required = false) Long ownerId,
                              @RequestParam(value = "categoryId", required = false) Long categoryId,
                              @RequestParam(value = "license", required = false) String license,
                              @RequestParam(value = "status", required = false) String status,
                              @RequestParam("page") int page, @RequestParam("size") int size);

    @DeleteMapping("/items/{softwareId}")
    ApiResponse<Object> deleteSoftware(@PathVariable("softwareId") Long softwareId);

    @PostMapping("/items/{softwareId}/ban")
    ApiResponse<Object> ban(@PathVariable("softwareId") Long softwareId,
                            @RequestBody Map<String, Object> body);

    @PostMapping("/items/{softwareId}/unban")
    ApiResponse<Object> unban(@PathVariable("softwareId") Long softwareId,
                              @RequestBody Map<String, Object> body);

    @PutMapping("/items/{softwareId}/featured")
    ApiResponse<Object> featured(@PathVariable("softwareId") Long softwareId,
                                 @RequestBody Map<String, Object> body);

    // ---------------- 分类与标签（software:category） ----------------

    @GetMapping("/categories")
    ApiResponse<Object> categories();

    @PostMapping("/categories")
    ApiResponse<Object> createCategory(@RequestBody Map<String, Object> body);

    @PutMapping("/categories/{categoryId}")
    ApiResponse<Object> updateCategory(@PathVariable("categoryId") Long categoryId,
                                       @RequestBody Map<String, Object> body);

    @DeleteMapping("/categories/{categoryId}")
    ApiResponse<Object> disableCategory(@PathVariable("categoryId") Long categoryId);

    @GetMapping("/tags")
    ApiResponse<Object> tags(@RequestParam(value = "keyword", required = false) String keyword,
                             @RequestParam("page") int page, @RequestParam("size") int size);

    @PostMapping("/tags")
    ApiResponse<Object> createTag(@RequestBody Map<String, Object> body);

    @PutMapping("/tags/{tagId}")
    ApiResponse<Object> updateTag(@PathVariable("tagId") Long tagId,
                                  @RequestBody Map<String, Object> body);

    @DeleteMapping("/tags/{tagId}")
    ApiResponse<Object> disableTag(@PathVariable("tagId") Long tagId);

    @GetMapping("/comments")
    ApiResponse<Object> commentsGlobal(
            @RequestParam(value = "softwareId", required = false) Long softwareId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("page") int page, @RequestParam("size") int size);

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

    // ---------------- 授权查询与审计（software:access:list / permission:audit） ----------------

    @GetMapping("/access-grants")
    ApiResponse<Object> grantPage(@RequestParam(value = "softwareId", required = false) Long softwareId,
                                  @RequestParam(value = "userId", required = false) Long userId,
                                  @RequestParam("page") int page, @RequestParam("size") int size);

    // ---------------- 专题与统计（P1：software:topic / software:stats） ----------------

    @GetMapping("/topics")
    ApiResponse<Object> topics(@RequestParam("page") int page, @RequestParam("size") int size);

    @GetMapping("/topics/{topicId}")
    ApiResponse<Object> topicDetail(@PathVariable("topicId") Long topicId);

    @PostMapping("/topics")
    ApiResponse<Object> createTopic(@RequestBody Map<String, Object> body);

    @PutMapping("/topics/{topicId}")
    ApiResponse<Object> updateTopic(@PathVariable("topicId") Long topicId,
                                    @RequestBody Map<String, Object> body);

    @DeleteMapping("/topics/{topicId}")
    ApiResponse<Object> disableTopic(@PathVariable("topicId") Long topicId);

    @GetMapping("/statistics")
    ApiResponse<Object> statistics();

    /** SF04：访问申请分页 */
    @GetMapping("/access-requests")
    ApiResponse<Object> accessRequests(@RequestParam("status") String status,
                                       @RequestParam("page") int page,
                                       @RequestParam("size") int size);

    /** SF04：访问申请决定（批准同事务授权并回写 grant_id） */
    @PostMapping("/access-requests/{requestId}/decision")
    ApiResponse<Object> decideAccessRequest(@PathVariable("requestId") Long requestId,
                                            @RequestBody Map<String, Object> body);

    /** SF03：发布者申请分页 */
    @GetMapping("/publisher-applications")
    ApiResponse<Object> publisherApplications(@RequestParam("status") String status,
                                              @RequestParam("page") int page,
                                              @RequestParam("size") int size);

    /** SF03：发布者申请决定（通过同事务授予资格） */
    @PostMapping("/publisher-applications/{applicationId}/decision")
    ApiResponse<Object> decidePublisherApplication(@PathVariable("applicationId") Long applicationId,
                                                   @RequestBody Map<String, Object> body);

    /** SW10：通知任务运维（software:fanout） */
    @GetMapping("/fanout/tasks")
    ApiResponse<Object> fanoutTasks(@RequestParam("page") int page,
                                    @RequestParam("size") int size);

    /** SW10：人工重试通知任务（沿用原发布事件身份） */
    @PostMapping("/fanout/tasks/{taskId}/retry")
    ApiResponse<Object> retryFanoutTask(@PathVariable("taskId") Long taskId);

    @GetMapping("/permission-audits")
    ApiResponse<Object> permissionAudits(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "softwareId", required = false) Long softwareId,
            @RequestParam("page") int page, @RequestParam("size") int size);
}
