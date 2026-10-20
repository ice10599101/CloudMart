package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.SoftwareFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PatchMapping;

/**
 * 小答软件管理代理（方案 7.1/7.5，S09 全量）：外部 /api/admin/software/** 经
 * admin 路由到本控制器，由 SoftwareFeignClient 内部转发。
 * 权限点与 7.5 表及 V2__software_admin_menu.sql 菜单种子一一对应：
 * 查询 software:list/software:review；处置 software:moderate/software:feature；
 * 评论 software:comment；举报 software:report；链接软件 software:link；
 * 分类标签 software:category；发布资格 software:publisher:list|manage；
 * 授权 software:access:list|manage；联系入口 software:contact:manage；
 * 审计 software:permission:audit。
 * 终端不可直调软件原生 /admin/**（网关拦截 + 软件服务服务令牌路径强校验）。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "管理端-小答软件", description = "审核/评论/资格/授权/联系入口/举报/反馈代理")
public class AdminSoftwareController {

    private final SoftwareFeignClient softwareFeignClient;
    private final com.cloudmart.admin.service.AdminUserService adminUserService;

    // ---------------- 审核（software:review） ----------------

    @OperLog(title = "软件审核", businessType = 2)
    @PostMapping("/revisions/{revisionId}/decision")
    @RequiresPermission("software:review")
    @Operation(summary = "审核决策", description = "通过/退回绑定提交批次；A07 防旧批次覆盖")
    public ApiResponse<Object> decision(
            @PathVariable("revisionId") Long revisionId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.decision(revisionId, body);
    }

    @OperLog(title = "软件来源", businessType = 2)
    @PostMapping("/{softwareId}/sources/state")
    @RequiresPermission("software:link")
    @Operation(summary = "来源状态处置", description = "核实/禁用/解除（4.3 指纹匹配应用）")
    public ApiResponse<Object> sourceState(
            @PathVariable("softwareId") Long softwareId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.sourceState(softwareId, body);
    }

    @GetMapping("/{softwareId}/sources")
    @RequiresPermission("software:link")
    @Operation(summary = "来源清单（SW06）", description = "当前线上快照按版本/平台/架构；链接脱敏，不含提取码")
    public ApiResponse<Object> sources(@PathVariable("softwareId") Long softwareId) {
        return softwareFeignClient.sources(softwareId);
    }

    @GetMapping("/revisions/pending")
    @RequiresPermission("software:review")
    @Operation(summary = "待审/检查列表",
            description = "status=PENDING（默认）或 CHECKING；按提交时间升序，含等待时长")
    public ApiResponse<Object> pending(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.pending(page, size, status);
    }

    @GetMapping("/{softwareId}/admin-detail")
    @RequiresPermission("software:list")
    @Operation(summary = "后台完整详情", description = "全快照+状态+审核历史（可读秘密字段）")
    public ApiResponse<Object> adminDetail(@PathVariable("softwareId") Long softwareId) {
        return softwareFeignClient.adminDetail(softwareId);
    }

    // ---------------- 评论管理（software:comment） ----------------

    @GetMapping("/{softwareId}/comments")
    @RequiresPermission("software:comment")
    @Operation(summary = "评论管理列表", description = "含 HIDDEN/PENDING/DELETED；原文仅可见/待审")
    public ApiResponse<Object> comments(
            @PathVariable("softwareId") Long softwareId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.comments(softwareId, status, page, size);
    }

    @OperLog(title = "软件评论", businessType = 2)
    @GetMapping("/comments")
    @RequiresPermission("software:comment")
    @Operation(summary = "评论管理全局列表",
            description = "不指定 softwareId 时跨软件；HIDDEN/DELETED 不回传原文")
    public ApiResponse<Object> commentsGlobal(
            @RequestParam(value = "softwareId", required = false) Long softwareId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.commentsGlobal(softwareId, status, page, size);
    }

    @PostMapping("/{softwareId}/comments/{commentId}/moderate")
    @RequiresPermission("software:comment")
    @Operation(summary = "评论人工处置", description = "APPROVE/HIDE/RESTORE/REJECT；幂等")
    public ApiResponse<Object> moderateComment(
            @PathVariable("softwareId") Long softwareId,
            @PathVariable("commentId") Long commentId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.moderateComment(softwareId, commentId, body);
    }

    // ---------------- 发布资格（software:publisher:list|manage） ----------------

    @GetMapping("/publisher-permissions")
    @RequiresPermission("software:publisher:list")
    @Operation(summary = "发布资格分页",
            description = "行内 userNickname（会员资料服务）与 updatedByName（后台账号表）已回填")
    public ApiResponse<Object> permissions(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        ApiResponse<Object> response = softwareFeignClient.permissions(page, size);
        fillOperatorNames(response);
        return response;
    }

    /** updated_by 是后台账号（admin_user），软件服务查不到——BFF 层回填操作人用户名 */
    @SuppressWarnings("unchecked")
    private void fillOperatorNames(ApiResponse<Object> response) {
        if (response == null || response.data() == null) {
            return;
        }
        Object data = response.data();
        List<Map<String, Object>> rows;
        if (data instanceof Map<?, ?> page && page.get("records") instanceof List<?> list) {
            rows = (List<Map<String, Object>>) list;
        } else if (data instanceof List<?> list) {
            rows = (List<Map<String, Object>>) list;
        } else {
            return;
        }
        Map<Long, String> nameById = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object raw = row.get("updatedBy");
            if (raw == null) {
                continue;
            }
            Long adminId;
            try {
                adminId = Long.parseLong(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                continue;
            }
            String name = nameById.computeIfAbsent(adminId, id -> {
                try {
                    var user = adminUserService.getById(id);
                    return user == null ? null
                            : (user.nickname() == null || user.nickname().isBlank()
                                    ? user.username() : user.nickname());
                } catch (Exception ignored) {
                    return null;
                }
            });
            if (name != null) {
                row.put("updatedByName", name);
            }
        }
    }

    @PostMapping("/publisher-permissions/batch")
    @RequiresPermission("software:publisher:list")
    @Operation(summary = "批量查询指定会员资格", description = "会员详情页；缺失记录=false")
    public ApiResponse<Object> permissionsOf(@RequestBody List<Long> userIds) {
        return softwareFeignClient.permissionsOf(userIds);
    }

    @OperLog(title = "软件发布资格", businessType = 2)
    @PostMapping("/publisher-permissions/{userId}/toggle")
    @RequiresPermission("software:publisher:manage")
    @Operation(summary = "开通/关闭发布资格",
            description = "开通前核对会员可用（失败 503）；权限审计；Idempotency-Key")
    public ApiResponse<Object> togglePermission(
            @PathVariable("userId") Long userId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.togglePermission(userId, body);
    }

    // ---------------- 付款授权（software:access:list|manage）与联系入口（software:contact:manage） ----------------

    @GetMapping("/{softwareId}/access-grants")
    @RequiresPermission("software:access:list")
    @Operation(summary = "付款授权列表", description = "状态/到期/确认备注/外部核对编号")
    public ApiResponse<Object> grants(@PathVariable("softwareId") Long softwareId) {
        return softwareFeignClient.grants(softwareId);
    }

    @OperLog(title = "软件查看授权", businessType = 1)
    @PostMapping("/{softwareId}/access-grants")
    @RequiresPermission("software:access:manage")
    @Operation(summary = "开通/延期授权", description = "重开更新同一行并追加审计；恰好到期即无效")
    public ApiResponse<Object> grant(
            @PathVariable("softwareId") Long softwareId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.grant(softwareId, body);
    }

    @OperLog(title = "软件查看授权", businessType = 3)
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
    @RequiresPermission("software:contact:manage")
    @Operation(summary = "联系入口列表", description = "启用+停用全量")
    public ApiResponse<Object> contacts() {
        return softwareFeignClient.contacts();
    }

    @OperLog(title = "联系入口", businessType = 1)
    @PostMapping("/access-contacts")
    @RequiresPermission("software:contact:manage")
    @Operation(summary = "新增/更新联系入口",
            description = "引用中仅停用不删除；HTTPS_URL 校验；CONTACT_UPDATE 审计")
    public ApiResponse<Object> saveContact(@RequestBody Map<String, Object> body) {
        return softwareFeignClient.saveContact(body);
    }

    // ---------------- 举报（software:report）与失效反馈（software:link） ----------------

    @GetMapping("/reports")
    @RequiresPermission("software:report")
    @Operation(summary = "举报列表", description = "status 过滤；创建时间升序")
    public ApiResponse<Object> reports(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.reports(status, page, size);
    }

    @OperLog(title = "软件举报处置", businessType = 2)
    @PostMapping("/reports/{reportId}/handle")
    @RequiresPermission("software:report")
    @Operation(summary = "举报处置",
            description = "RESOLVED+显式 action（NONE/BAN_SOFTWARE/HIDE_COMMENT）或 REJECTED；"
                    + "不隐式封禁")
    public ApiResponse<Object> handleReport(
            @PathVariable("reportId") Long reportId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.handleReport(reportId, body);
    }

    @GetMapping("/link-feedbacks")
    @RequiresPermission("software:link")
    @Operation(summary = "失效反馈列表", description = "status 过滤")
    public ApiResponse<Object> linkFeedbacks(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "reasonCode", required = false) String reasonCode,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.linkFeedbacks(status, reasonCode, page, size);
    }

    @OperLog(title = "软件反馈处理", businessType = 2)
    @PostMapping("/link-feedbacks/{feedbackId}/mark")
    @RequiresPermission("software:link")
    @Operation(summary = "反馈标记",
            description = "RESOLVED 必须携带修复 revisionId（保留 original 指纹）")
    public ApiResponse<Object> markFeedback(
            @PathVariable("feedbackId") Long feedbackId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.markFeedback(feedbackId, body);
    }

    // ---------------- 来源申诉（SF08 补批，software:link） ----------------

    @GetMapping("/source-appeals")
    @RequiresPermission("software:link")
    @Operation(summary = "作者来源申诉列表",
            description = "status=PENDING/ACCEPTED/REJECTED；含原处置快照（并列查看）")
    public ApiResponse<Object> sourceAppeals(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.sourceAppeals(status, page, size);
    }

    @OperLog(title = "来源申诉处理", businessType = 2)
    @PostMapping("/source-appeals/{appealId}/handle")
    @RequiresPermission("software:link")
    @Operation(summary = "处理来源申诉",
            description = "ACCEPTED 仅恢复为 UNKNOWN 待重检（不直接判可用）；REJECTED 保留原处置；"
                    + "只处理一次，重复同结论幂等")
    public ApiResponse<Object> handleSourceAppeal(
            @PathVariable("appealId") Long appealId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.handleSourceAppeal(appealId, body);
    }

    // ---------------- 目录（software:list） ----------------

    @GetMapping("/items")
    @RequiresPermission("software:list")
    @Operation(summary = "软件目录",
            description = "keyword/ownerId/categoryId/license/status 筛选；"
                    + "含线上、待审、精选、下架、封禁（不含已删除）")
    public ApiResponse<Object> items(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "license", required = false) String license,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.items(keyword, ownerId, categoryId, license, status, page, size);
    }

    @GetMapping("/revisions/{revisionId}")
    @RequiresPermission("software:review")
    @Operation(summary = "审核快照对比",
            description = "目标快照 + 当前线上快照（含链接/提取码，仅审核域）；前端标出变化")
    public ApiResponse<Object> reviewDetail(@PathVariable("revisionId") Long revisionId) {
        return softwareFeignClient.reviewDetail(revisionId);
    }

    // ---------------- 封禁/解除（software:moderate）与精选（software:feature） ----------------

    @DeleteMapping("/items/{softwareId}")
    @RequiresPermission("software:moderate")
    @OperLog(title = "软件管理", businessType = 3)
    @Operation(summary = "管理员删除软件", description = "软删 lifecycle=DELETED，幂等；公开面即时隐藏")
    public ApiResponse<Object> deleteSoftware(@PathVariable("softwareId") Long softwareId) {
        return softwareFeignClient.deleteSoftware(softwareId);
    }

    @OperLog(title = "软件处置", businessType = 2)
    @PostMapping("/items/{softwareId}/ban")
    @RequiresPermission("software:moderate")
    @Operation(summary = "独立封禁",
            description = "必须原因；BANNED 后作者不可自行恢复；通知分享者")
    public ApiResponse<Object> ban(
            @PathVariable("softwareId") Long softwareId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.ban(softwareId, body);
    }

    @OperLog(title = "软件处置", businessType = 2)
    @PostMapping("/items/{softwareId}/unban")
    @RequiresPermission("software:moderate")
    @Operation(summary = "解除封禁", description = "BANNED → UNLISTED，由作者恢复展示")
    public ApiResponse<Object> unban(
            @PathVariable("softwareId") Long softwareId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.unban(softwareId, body);
    }

    @OperLog(title = "软件精选", businessType = 2)
    @PutMapping("/items/{softwareId}/featured")
    @RequiresPermission("software:feature")
    @Operation(summary = "精选排序", description = "featuredRank=0 取消精选；值越大越靠前")
    public ApiResponse<Object> featured(
            @PathVariable("softwareId") Long softwareId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.featured(softwareId, body);
    }

    // ---------------- 同类关系（UX03；software:manage 权限） ----------------

    @GetMapping("/items/{softwareId}/relations")
    @RequiresPermission("software:manage")
    @Operation(summary = "同类关系清单", description = "管理视图全量（不过滤可见性）")
    public ApiResponse<Object> relations(@PathVariable("softwareId") Long softwareId) {
        return softwareFeignClient.relations(softwareId);
    }

    @OperLog(title = "同类关系维护", businessType = 2)
    @PutMapping("/items/{softwareId}/relations")
    @RequiresPermission("software:manage")
    @Operation(summary = "同类关系全量替换",
            description = "≤4 条；禁自关联/去重/目标须已发布；type=SIMILAR_USE/ALTERNATIVE_PLATFORM")
    public ApiResponse<Object> saveRelations(
            @PathVariable("softwareId") Long softwareId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.saveRelations(softwareId, body);
    }

    // ---------------- 软件需求（UX06；software:moderate 复用审核权限） ----------------

    @GetMapping("/requests")
    @RequiresPermission("software:moderate")
    @Operation(summary = "软件需求分页", description = "auditStatus/status 筛选；含来源关键词")
    public ApiResponse<Object> requests(
            @RequestParam(value = "auditStatus", required = false) String auditStatus,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return softwareFeignClient.requests(auditStatus, status, page, size);
    }

    @OperLog(title = "软件需求审核", businessType = 2)
    @PostMapping("/requests/{requestId}/audit")
    @RequiresPermission("software:moderate")
    @Operation(summary = "审核软件需求", description = "拒绝必须原因")
    public ApiResponse<Object> auditRequest(
            @PathVariable("requestId") Long requestId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.auditRequest(requestId, body);
    }

    @OperLog(title = "软件需求标记已找到", businessType = 2)
    @PostMapping("/requests/{requestId}/matched")
    @RequiresPermission("software:moderate")
    @Operation(summary = "标记已找到", description = "目标必须当前可见软件")
    public ApiResponse<Object> markRequestMatched(
            @PathVariable("requestId") Long requestId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.markRequestMatched(requestId, body);
    }

    // ---------------- 结构化声明（UX04-B；software:manage） ----------------

    @GetMapping("/items/{softwareId}/declared")
    @RequiresPermission("software:manage")
    @Operation(summary = "结构化声明读取", description = "null=未提供")
    public ApiResponse<Object> declaredOf(@PathVariable("softwareId") Long softwareId) {
        return softwareFeignClient.declaredOf(softwareId);
    }

    @OperLog(title = "结构化声明维护", businessType = 2)
    @PutMapping("/items/{softwareId}/declared")
    @RequiresPermission("software:manage")
    @Operation(summary = "结构化声明保存", description = "是否开源/离线可用；不伪造测试结论")
    public ApiResponse<Object> saveDeclared(
            @PathVariable("softwareId") Long softwareId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.saveDeclared(softwareId,
                idempotencyHeaders(idempotencyKey, body));
    }

    // ---------------- 教程（UX04；software:guide:list/manage） ----------------

    @GetMapping("/guides")
    @RequiresPermission("software:guide:list")
    @Operation(summary = "教程管理分页", description = "含未启用与文章非公开标注")
    public ApiResponse<Object> guides(
            @RequestParam(value = "guideType", required = false) String guideType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return softwareFeignClient.guides(guideType, page, size);
    }

    @OperLog(title = "教程维护", businessType = 2)
    @PostMapping("/guides")
    @RequiresPermission("software:guide:manage")
    @Operation(summary = "新增教程", description = "articleId 预检公开状态；幂等键")
    public ApiResponse<Object> createGuide(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.createGuide(idempotencyHeaders(idempotencyKey, body));
    }

    @OperLog(title = "教程维护", businessType = 2)
    @PostMapping("/guides/{guideId}")
    @RequiresPermission("software:guide:manage")
    @Operation(summary = "更新教程", description = "整体保存；幂等键")
    public ApiResponse<Object> updateGuide(
            @PathVariable("guideId") Long guideId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.updateGuide(guideId, idempotencyHeaders(idempotencyKey, body));
    }

    @OperLog(title = "教程维护", businessType = 2)
    @PostMapping("/guides/{guideId}/toggle")
    @RequiresPermission("software:guide:manage")
    @Operation(summary = "启用/停用教程", description = "停用后公开入口立即隐藏")
    public ApiResponse<Object> toggleGuide(
            @PathVariable("guideId") Long guideId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.toggleGuide(guideId, idempotencyHeaders(idempotencyKey, body));
    }

    @OperLog(title = "教程维护", businessType = 2)
    @DeleteMapping("/guides/{guideId}")
    @RequiresPermission("software:guide:manage")
    @Operation(summary = "删除教程", description = "软删除；重复删除幂等")
    public ApiResponse<Object> deleteGuide(
            @PathVariable("guideId") Long guideId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return softwareFeignClient.deleteGuide(guideId, idempotencyKey);
    }

    // ---------------- 首页内容（UX01；software:home:list/manage/preview） ----------------

    @GetMapping("/home-sections")
    @RequiresPermission("software:home:list")
    @Operation(summary = "首页区块管理列表", description = "含未启用；编辑视图（含 MANUAL 条目）")
    public ApiResponse<Object> homeSections() {
        return softwareFeignClient.homeSections();
    }

    @OperLog(title = "首页内容维护", businessType = 2)
    @PostMapping("/home-sections")
    @RequiresPermission("software:home:manage")
    @Operation(summary = "新增首页区块", description = "kind/data_source 白名单；幂等键")
    public ApiResponse<Object> createHomeSection(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.createHomeSection(idempotencyHeaders(idempotencyKey, body));
    }

    @OperLog(title = "首页内容维护", businessType = 2)
    @PatchMapping("/home-sections/{sectionId}")
    @RequiresPermission("software:home:manage")
    @Operation(summary = "更新首页区块", description = "整体保存；幂等键")
    public ApiResponse<Object> updateHomeSection(
            @PathVariable("sectionId") Long sectionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.updateHomeSection(sectionId,
                idempotencyHeaders(idempotencyKey, body));
    }

    @OperLog(title = "首页内容维护", businessType = 2)
    @PostMapping("/home-sections/{sectionId}/toggle")
    @RequiresPermission("software:home:manage")
    @Operation(summary = "启用/停用首页区块", description = "停用后公共聚合立即不返回；幂等键")
    public ApiResponse<Object> toggleHomeSection(
            @PathVariable("sectionId") Long sectionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.toggleHomeSection(sectionId,
                idempotencyHeaders(idempotencyKey, body));
    }

    @OperLog(title = "首页内容维护", businessType = 2)
    @DeleteMapping("/home-sections/{sectionId}")
    @RequiresPermission("software:home:manage")
    @Operation(summary = "删除首页区块", description = "软删除；重复删除幂等")
    public ApiResponse<Object> deleteHomeSection(
            @PathVariable("sectionId") Long sectionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return softwareFeignClient.deleteHomeSection(sectionId, idempotencyKey);
    }
    @OperLog(title = "首页内容维护", businessType = 2)
    @PostMapping("/home-sections/reorder")
    @RequiresPermission("software:home:manage")
    @Operation(summary = "首页区块原子重排", description = "完整集合校验（缺失/重复拒绝）")
    public ApiResponse<Object> reorderHomeSections(@RequestBody Map<String, Object> body) {
        return softwareFeignClient.reorderHomeSections(body);
    }

    @GetMapping("/home-sections/preview")
    @RequiresPermission("software:home:preview")
    @Operation(summary = "首页内容预览", description = "含未启用内容（LIVE/OFFLINE 标记）；只对管理员可读")
    public ApiResponse<Object> homePreview(
            @RequestParam(value = "platform", required = false) String platform) {
        return softwareFeignClient.homePreview(platform);
    }

    /** 将幂等键并入请求体转发（mall-software 读取 Idempotency-Key 头；经 Feign 需透传） */
    private Map<String, Object> idempotencyHeaders(String idempotencyKey, Map<String, Object> body) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            body.put("idempotencyKey", idempotencyKey);
        }
        return body;
    }

    // ---------------- 分类与标签（software:category） ----------------

    @GetMapping("/categories")
    @RequiresPermission("software:category")
    @Operation(summary = "分类列表（含禁用）", description = "运营视图；按 sortOrder 升序")
    public ApiResponse<Object> categories() {
        return softwareFeignClient.categories();
    }

    @OperLog(title = "软件分类", businessType = 1)
    @PostMapping("/categories")
    @RequiresPermission("software:category")
    @Operation(summary = "新增分类", description = "slug 唯一")
    public ApiResponse<Object> createCategory(@RequestBody Map<String, Object> body) {
        return softwareFeignClient.createCategory(body);
    }

    @OperLog(title = "软件分类", businessType = 2)
    @PutMapping("/categories/{categoryId}")
    @RequiresPermission("software:category")
    @Operation(summary = "更新分类", description = "含启用/禁用与排序调整")
    public ApiResponse<Object> updateCategory(
            @PathVariable("categoryId") Long categoryId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.updateCategory(categoryId, body);
    }

    @OperLog(title = "软件分类", businessType = 3)
    @DeleteMapping("/categories/{categoryId}")
    @RequiresPermission("software:category")
    @Operation(summary = "禁用分类", description = "DELETE 语义为禁用，不做物理删除")
    public ApiResponse<Object> disableCategory(@PathVariable("categoryId") Long categoryId) {
        return softwareFeignClient.disableCategory(categoryId);
    }

    @GetMapping("/tags")
    @RequiresPermission("software:category")
    @Operation(summary = "标签分页（含禁用）", description = "keyword 匹配标签名")
    public ApiResponse<Object> tags(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.tags(keyword, page, size);
    }

    @OperLog(title = "软件标签", businessType = 1)
    @PostMapping("/tags")
    @RequiresPermission("software:category")
    @Operation(summary = "新增标签", description = "规范化去重")
    public ApiResponse<Object> createTag(@RequestBody Map<String, Object> body) {
        return softwareFeignClient.createTag(body);
    }

    @OperLog(title = "软件标签", businessType = 2)
    @PutMapping("/tags/{tagId}")
    @RequiresPermission("software:category")
    @Operation(summary = "更新标签", description = "含启用/禁用")
    public ApiResponse<Object> updateTag(
            @PathVariable("tagId") Long tagId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.updateTag(tagId, body);
    }

    @OperLog(title = "软件标签", businessType = 3)
    @DeleteMapping("/tags/{tagId}")
    @RequiresPermission("software:category")
    @Operation(summary = "禁用标签", description = "DELETE 语义为禁用，不做物理删除")
    public ApiResponse<Object> disableTag(@PathVariable("tagId") Long tagId) {
        return softwareFeignClient.disableTag(tagId);
    }

    // ---------------- 授权查询与审计（software:access:list / permission:audit） ----------------

    @GetMapping("/access-grants")
    @RequiresPermission("software:access:list")
    @Operation(summary = "付款授权分页",
            description = "按软件/会员搜索；展示口径 ACTIVE/EXPIRED/REVOKED")
    public ApiResponse<Object> grantPage(
            @RequestParam(value = "softwareId", required = false) Long softwareId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.grantPage(softwareId, userId, page, size);
    }

    @GetMapping("/permission-audits")
    @RequiresPermission("software:permission:audit")
    @Operation(summary = "权限变更审计",
            description = "发布资格/付款授权/联系入口操作历史；按会员或软件过滤")
    public ApiResponse<Object> permissionAudits(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "softwareId", required = false) Long softwareId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.permissionAudits(userId, softwareId, page, size);
    }

    // ---------------- 专题（P1：software:topic） ----------------

    @GetMapping("/topics")
    @RequiresPermission("software:topic")
    @Operation(summary = "专题分页（含草稿/禁用）", description = "P1")
    public ApiResponse<Object> topics(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.topics(page, size);
    }

    @GetMapping("/topics/{topicId}")
    @RequiresPermission("software:topic")
    @Operation(summary = "专题编辑读取", description = "专题 + 原始清单（不过滤可见性）")
    public ApiResponse<Object> topicDetail(@PathVariable("topicId") Long topicId) {
        return softwareFeignClient.topicDetail(topicId);
    }

    @OperLog(title = "软件专题", businessType = 1)
    @PostMapping("/topics")
    @RequiresPermission("software:topic")
    @Operation(summary = "新增专题", description = "清单全量替换；slug 唯一")
    public ApiResponse<Object> createTopic(@RequestBody Map<String, Object> body) {
        return softwareFeignClient.createTopic(body);
    }

    @OperLog(title = "软件专题", businessType = 2)
    @PutMapping("/topics/{topicId}")
    @RequiresPermission("software:topic")
    @Operation(summary = "更新专题", description = "含上线/下线与清单整体替换")
    public ApiResponse<Object> updateTopic(
            @PathVariable("topicId") Long topicId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.updateTopic(topicId, body);
    }

    @OperLog(title = "软件专题", businessType = 3)
    @DeleteMapping("/topics/{topicId}")
    @RequiresPermission("software:topic")
    @Operation(summary = "禁用专题", description = "DELETE 语义为禁用")
    public ApiResponse<Object> disableTopic(@PathVariable("topicId") Long topicId) {
        return softwareFeignClient.disableTopic(topicId);
    }

    // ---------------- 统计（P1：software:stats） ----------------

    @GetMapping("/statistics/trends")
    @RequiresPermission("software:stats")
    @Operation(summary = "运营趋势", description = "日序列（获取/收藏/订阅）+ 可选效率指标；最长 90 天")
    public ApiResponse<Object> statTrends(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "softwareId", required = false) Long softwareId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "publisherId", required = false) Long publisherId,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "withEfficiency", defaultValue = "false") boolean withEfficiency) {
        return softwareFeignClient.statTrends(from, to, softwareId, categoryId,
                publisherId, platform, withEfficiency);
    }

    @GetMapping("/statistics")
    @RequiresPermission("software:stats")
    @Operation(summary = "运营统计总览",
            description = "在架/待审/平均审核分钟/获取链接次数与人数/收藏/新评分/待办；口径随响应返回")
    public ApiResponse<Object> statistics() {
        return softwareFeignClient.statistics();
    }

    // ---------------- 访问申请（SF04：software:access:list / manage） ----------------

    @GetMapping("/access-requests")
    @RequiresPermission("software:access:list")
    @Operation(summary = "访问申请列表", description = "按状态筛选；证据与凭据仅授权员可见")
    public ApiResponse<Object> accessRequests(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.accessRequests(status, page, size);
    }

    @OperLog(title = "访问申请决定", businessType = 2)
    @PostMapping("/access-requests/{requestId}/decision")
    @RequiresPermission("software:access:manage")
    @Operation(summary = "访问申请决定", description = "批准同事务调用既有授权服务并回写 grant_id")
    public ApiResponse<Object> decideAccessRequest(
            @PathVariable("requestId") Long requestId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.decideAccessRequest(requestId, body);
    }

    // ---------------- 来源健康巡检（SF07：software:link） ----------------

    @OperLog(title = "来源巡检重检", businessType = 2)
    @PostMapping("/sources/{sourceId}/patrol")
    @RequiresPermission("software:link")
    @Operation(summary = "来源手动重检", description = "立即探测单条 HTTPS 来源；不覆盖人工状态；磁力/种子不支持")
    public ApiResponse<Object> patrolSource(@PathVariable("sourceId") Long sourceId) {
        return softwareFeignClient.patrolSource(sourceId);
    }

    // ---------------- 审核工作流（SF08：software:review） ----------------

    @OperLog(title = "审核领取", businessType = 2)
    @PostMapping("/revisions/{revisionId}/claim")
    @RequiresPermission("software:review")
    @Operation(summary = "领取待审提交", description = "协作标记；他人未超时领取被拒；超时 1 小时惰性释放")
    public ApiResponse<Object> claimReview(@PathVariable("revisionId") Long revisionId) {
        return softwareFeignClient.claimReview(revisionId);
    }

    @OperLog(title = "审核释放", businessType = 2)
    @PostMapping("/revisions/{revisionId}/release")
    @RequiresPermission("software:review")
    @Operation(summary = "释放领取", description = "仅领取人本人；幂等")
    public ApiResponse<Object> releaseReview(@PathVariable("revisionId") Long revisionId) {
        return softwareFeignClient.releaseReview(revisionId);
    }

    @GetMapping("/items/{softwareId}/history")
    @RequiresPermission("software:review")
    @Operation(summary = "历史内容", description = "字段 diff + 人工决定 + 来源状态；只读")
    public ApiResponse<Object> softwareHistory(
            @PathVariable("softwareId") Long softwareId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.softwareHistory(softwareId, page, size);
    }

    // ---------------- 发布者申请（SF03：software:publisher:list / manage） ----------------

    @GetMapping("/publisher-applications")
    @RequiresPermission("software:publisher:list")
    @Operation(summary = "发布者申请列表", description = "按状态筛选：PENDING/APPROVED/REJECTED/CANCELLED")
    public ApiResponse<Object> publisherApplications(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.publisherApplications(status, page, size);
    }

    @OperLog(title = "发布者申请决定", businessType = 2)
    @PostMapping("/publisher-applications/{applicationId}/decision")
    @RequiresPermission("software:publisher:manage")
    @Operation(summary = "发布者申请决定", description = "通过同事务授予资格并写 targetUserId 审计")
    public ApiResponse<Object> decidePublisherApplication(
            @PathVariable("applicationId") Long applicationId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.decidePublisherApplication(applicationId, body);
    }

    // ---------------- 通知任务运维（SW10：software:fanout，权限独立） ----------------

    @GetMapping("/fanout/tasks")
    @RequiresPermission("software:fanout")
    @Operation(summary = "通知与检查任务列表",
            description = "状态/游标/失败原因/下次执行时间/投递聚合（SW10）")
    public ApiResponse<Object> fanoutTasks(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.fanoutTasks(page, size);
    }

    @OperLog(title = "通知任务重试", businessType = 2)
    @PostMapping("/fanout/tasks/{taskId}/retry")
    @RequiresPermission("software:fanout")
    @Operation(summary = "人工重试通知任务",
            description = "重新入队；沿用原发布事件身份（seq/revisionId 不变）")
    public ApiResponse<Object> retryFanoutTask(@PathVariable("taskId") Long taskId) {
        return softwareFeignClient.retryFanoutTask(taskId);
    }

    @GetMapping("/fanout/check-tasks")
    @RequiresPermission("software:fanout")
    @Operation(summary = "自动检查任务列表",
            description = "SW04：CHECKING 检查任务状态/尝试次数/下次重试/最后错误")
    public ApiResponse<Object> checkTasks(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return softwareFeignClient.checkTasks(status, page, size);
    }

    @OperLog(title = "自动检查重试", businessType = 2)
    @PostMapping("/fanout/check-tasks/{taskId}/retry")
    @RequiresPermission("software:fanout")
    @Operation(summary = "人工重试自动检查",
            description = "仅提交仍处于 CHECKING 且无有效租约；重新入队沿用原幂等身份")
    public ApiResponse<Object> retryCheckTask(@PathVariable("taskId") Long taskId) {
        return softwareFeignClient.retryCheckTask(taskId);
    }
}
