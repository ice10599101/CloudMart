package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.SoftwareSnapshotInput;
import com.cloudmart.software.policy.SoftwarePublisherPolicy;
import com.cloudmart.software.service.SoftwarePublishService;
import com.cloudmart.software.vo.software.SoftwareCreatedVO;
import com.cloudmart.software.vo.software.SoftwareEditorVO;
import com.cloudmart.software.vo.software.SoftwareMineItemVO;
import com.cloudmart.software.vo.software.SoftwareSubmittedVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小答软件作者端点（方案 7.3）：登录必达（X-User-Id 为已验签主体）；
 * 固定路径（publisher-status/mine 等）在 SecurityConfig 先于 /{id} 声明。
 * 受限写入的发布资格矩阵在 Service 层强制应用（幂等重试同样检查）。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "小答软件-作者", description = "创建/编辑/提交/撤回/上下架/删除/我的软件")
public class SoftwarePublishController {

    private final SoftwarePublishService softwarePublishService;
    private final SoftwarePublisherPolicy publisherPolicy;

    @GetMapping("/publisher-status")
    @Operation(summary = "发布资格状态", description = "仅 UI 提示；写接口仍实时重查（4.0）")
    public ApiResponse<SoftwarePublisherPolicy.UiHint> publisherStatus(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(publisherPolicy.uiHint(userId));
    }

    /** 固定路径必须先于 /{id} 可匹配（Spring 字面量优先于模板，防 "access-contacts" 撞 {id} 转 Long 失败） */
    @GetMapping("/access-contacts")
    @Operation(summary = "作者侧可选联系入口", description = "仅启用项；ADMIN_PAID 查看条件的选择来源（7.3）")
    public ApiResponse<List<Map<String, Object>>> accessContacts() {
        return ApiResponse.ok(softwarePublishService.listEnabledAccessContacts());
    }

    @PostMapping
    @Operation(summary = "创建软件", description = "Idempotency-Key 幂等；返回 softwareId/workingRevisionId")
    public ApiResponse<SoftwareCreatedVO> create(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(softwarePublishService.create(userId, idempotencyKey));
    }

    @PostMapping("/{id}/working-revision")
    @Operation(summary = "克隆工作快照", description = "已有工作副本直接返回；否则克隆 published（仅本人）")
    public ApiResponse<SoftwareEditorVO> workingRevision(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(softwarePublishService.workingRevision(userId, id, idempotencyKey));
    }

    @GetMapping("/{id}/editor")
    @Operation(summary = "编辑器完整工作数据",
            description = "本人专享（含链接/提取码/审核原因）；独立鉴权，与公开 DTO 隔离")
    public ApiResponse<SoftwareEditorVO> editor(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id) {
        return ApiResponse.ok(softwarePublishService.editor(userId, id));
    }

    @PutMapping("/{id}/revisions/{revisionId}")
    @Operation(summary = "保存草稿",
            description = "仅 DRAFT/REJECTED；全量替换子项；白名单字段（版本号机制已移除，保存即生效）")
    public ApiResponse<SoftwareEditorVO> saveDraft(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @PathVariable("revisionId") Long revisionId,
            @RequestBody SoftwareSnapshotInput input) {
        return ApiResponse.ok(softwarePublishService.saveDraft(
                userId, id, revisionId, input));
    }

    @PatchMapping("/{id}/revisions/{revisionId}")
    @Operation(summary = "局部保存草稿（SW02）",
            description = "仅应用请求中出现的字段；缺省字段保持现值；null 清空可空标量；数组整体替换集合")
    public ApiResponse<SoftwareEditorVO> patchDraft(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @PathVariable("revisionId") Long revisionId,
            @RequestBody tools.jackson.databind.JsonNode patch) {
        return ApiResponse.ok(softwarePublishService.patchDraft(userId, id, revisionId, patch));
    }

    @PostMapping("/{id}/revisions/{revisionId}/submit")
    @Operation(summary = "提交审核",
            description = "资格+开关+完整性+社区审核绑定结果；冻结该不可变快照为 PENDING 并返回 revisionId")
    public ApiResponse<SoftwareSubmittedVO> submit(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @PathVariable("revisionId") Long revisionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(softwarePublishService.submit(
                userId, id, revisionId, idempotencyKey));
    }

    @PostMapping("/{id}/revisions/{revisionId}/withdraw")
    @Operation(summary = "撤回待审", description = "PENDING→DRAFT（不要求资格，保留整理既有内容能力）")
    public ApiResponse<SoftwareEditorVO> withdraw(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @PathVariable("revisionId") Long revisionId
            ) {
        return ApiResponse.ok(softwarePublishService.withdraw(userId, id, revisionId));
    }

    @PostMapping("/{id}/unlist")
    @Operation(summary = "下架", description = "ACTIVE→UNLISTED（不要求资格）")
    public ApiResponse<SoftwareEditorVO> unlist(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id
            ) {
        return ApiResponse.ok(softwarePublishService.unlist(userId, id));
    }

    @PostMapping("/{id}/relist")
    @Operation(summary = "重新上架",
            description = "UNLISTED→ACTIVE，须有合格已审快照；要求发布资格；BANNED 拒绝")
    public ApiResponse<SoftwareEditorVO> relist(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id
            ) {
        return ApiResponse.ok(softwarePublishService.relist(userId, id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除软件", description = "软删除（审计保留）；幂等")
    public ApiResponse<Void> delete(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id
            ) {
        softwarePublishService.delete(userId, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/mine")
    @Operation(summary = "我的软件",
            description = "tab=published/draft/pending/rejected/favorites（subscriptions 为 P1）；只查本人")
    public ApiResponse<Page<SoftwareMineItemVO>> mine(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(value = "tab", defaultValue = "published") String tab,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(softwarePublishService.mine(userId, tab, page, size));
    }
}
