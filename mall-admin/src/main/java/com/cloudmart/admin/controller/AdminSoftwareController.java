package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.SoftwareFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 小答软件管理代理（方案 7.1/7.5，S09 全量）：外部 /api/admin/software/** 经
 * admin 路由到本控制器，由 SoftwareFeignClient 内部转发。
 * 权限点：软件审核 software:review；发布资格 software:publisher:manage；
 * 授权与联系入口 software:access:manage；举报与反馈 software:report:manage。
 * 终端不可直调软件原生 /admin/**（网关拦截 + 软件服务服务令牌路径强校验）。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "管理端-小答软件", description = "审核/评论/资格/授权/联系入口/举报/反馈代理")
public class AdminSoftwareController {

    private final SoftwareFeignClient softwareFeignClient;

    // ---------------- 审核（software:review） ----------------

    @PostMapping("/revisions/{revisionId}/decision")
    @RequiresPermission("software:review")
    @Operation(summary = "审核决策", description = "通过/退回绑定提交批次；A07 防旧批次覆盖")
    public ApiResponse<Object> decision(
            @PathVariable("revisionId") Long revisionId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.decision(revisionId, body);
    }

    @PostMapping("/{softwareId}/sources/state")
    @RequiresPermission("software:review")
    @Operation(summary = "来源状态处置", description = "核实/禁用/解除（4.3 指纹匹配应用）")
    public ApiResponse<Object> sourceState(
            @PathVariable("softwareId") Long softwareId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.sourceState(softwareId, body);
    }

    @GetMapping("/revisions/pending")
    @RequiresPermission("software:review")
    @Operation(summary = "待审列表", description = "PENDING 快照按提交时间升序")
    public ApiResponse<Object> pending(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.pending(page, size);
    }

    @GetMapping("/{softwareId}/admin-detail")
    @RequiresPermission("software:review")
    @Operation(summary = "后台完整详情", description = "全快照+状态+审核历史（可读秘密字段）")
    public ApiResponse<Object> adminDetail(@PathVariable("softwareId") Long softwareId) {
        return softwareFeignClient.adminDetail(softwareId);
    }

    // ---------------- 评论管理（software:report:manage，处置域） ----------------

    @GetMapping("/{softwareId}/comments")
    @RequiresPermission("software:report:manage")
    @Operation(summary = "评论管理列表", description = "含 HIDDEN/PENDING/DELETED；原文仅可见/待审")
    public ApiResponse<Object> comments(
            @PathVariable("softwareId") Long softwareId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.comments(softwareId, status, page, size);
    }

    @PostMapping("/{softwareId}/comments/{commentId}/moderate")
    @RequiresPermission("software:report:manage")
    @Operation(summary = "评论人工处置", description = "APPROVE/HIDE/RESTORE/REJECT；幂等")
    public ApiResponse<Object> moderateComment(
            @PathVariable("softwareId") Long softwareId,
            @PathVariable("commentId") Long commentId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.moderateComment(softwareId, commentId, body);
    }

    // ---------------- 发布资格（software:publisher:manage） ----------------

    @GetMapping("/publisher-permissions")
    @RequiresPermission("software:publisher:manage")
    @Operation(summary = "发布资格分页", description = "前端拼装会员资料后展示")
    public ApiResponse<Object> permissions(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.permissions(page, size);
    }

    @PostMapping("/publisher-permissions/batch")
    @RequiresPermission("software:publisher:manage")
    @Operation(summary = "批量查询指定会员资格", description = "会员详情页；缺失记录=false")
    public ApiResponse<Object> permissionsOf(@RequestBody List<Long> userIds) {
        return softwareFeignClient.permissionsOf(userIds);
    }

    @PostMapping("/publisher-permissions/{userId}/toggle")
    @RequiresPermission("software:publisher:manage")
    @Operation(summary = "开通/关闭发布资格",
            description = "开通前核对会员可用（失败 503）；权限审计；Idempotency-Key")
    public ApiResponse<Object> togglePermission(
            @PathVariable("userId") Long userId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.togglePermission(userId, body);
    }

    // ---------------- 付款授权与联系入口（software:access:manage） ----------------

    @GetMapping("/{softwareId}/access-grants")
    @RequiresPermission("software:access:manage")
    @Operation(summary = "付款授权列表", description = "状态/到期/确认备注/外部核对编号")
    public ApiResponse<Object> grants(@PathVariable("softwareId") Long softwareId) {
        return softwareFeignClient.grants(softwareId);
    }

    @PostMapping("/{softwareId}/access-grants")
    @RequiresPermission("software:access:manage")
    @Operation(summary = "开通/延期授权", description = "重开更新同一行并追加审计；恰好到期即无效")
    public ApiResponse<Object> grant(
            @PathVariable("softwareId") Long softwareId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.grant(softwareId, body);
    }

    @PostMapping("/{softwareId}/access-grants/{userId}/revoke")
    @RequiresPermission("software:access:manage")
    @Operation(summary = "撤销授权", description = "幂等；已撤销重复调用无副作用")
    public ApiResponse<Object> revokeGrant(
            @PathVariable("softwareId") Long softwareId,
            @PathVariable("userId") Long userId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.revokeGrant(softwareId, userId, body);
    }

    @GetMapping("/access-contacts")
    @RequiresPermission("software:access:manage")
    @Operation(summary = "联系入口列表", description = "启用+停用全量")
    public ApiResponse<Object> contacts() {
        return softwareFeignClient.contacts();
    }

    @PostMapping("/access-contacts")
    @RequiresPermission("software:access:manage")
    @Operation(summary = "新增/更新联系入口",
            description = "引用中仅停用不删除；HTTPS_URL 校验；CONTACT_UPDATE 审计")
    public ApiResponse<Object> saveContact(@RequestBody Map<String, Object> body) {
        return softwareFeignClient.saveContact(body);
    }

    // ---------------- 举报与失效反馈（software:report:manage） ----------------

    @GetMapping("/reports")
    @RequiresPermission("software:report:manage")
    @Operation(summary = "举报列表", description = "status 过滤；创建时间升序")
    public ApiResponse<Object> reports(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.reports(status, page, size);
    }

    @PostMapping("/reports/{reportId}/handle")
    @RequiresPermission("software:report:manage")
    @Operation(summary = "举报处置",
            description = "RESOLVED+显式 action（NONE/BAN_SOFTWARE/HIDE_COMMENT）或 REJECTED；"
                    + "不隐式封禁")
    public ApiResponse<Object> handleReport(
            @PathVariable("reportId") Long reportId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.handleReport(reportId, body);
    }

    @GetMapping("/link-feedbacks")
    @RequiresPermission("software:report:manage")
    @Operation(summary = "失效反馈列表", description = "status 过滤")
    public ApiResponse<Object> linkFeedbacks(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.linkFeedbacks(status, page, size);
    }

    @PostMapping("/link-feedbacks/{feedbackId}/mark")
    @RequiresPermission("software:report:manage")
    @Operation(summary = "反馈标记",
            description = "RESOLVED 必须携带修复 revisionId（保留 original 指纹）")
    public ApiResponse<Object> markFeedback(
            @PathVariable("feedbackId") Long feedbackId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.markFeedback(feedbackId, body);
    }
}
