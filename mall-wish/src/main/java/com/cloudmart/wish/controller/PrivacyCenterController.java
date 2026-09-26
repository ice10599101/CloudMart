package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.enums.ConsentType;
import com.cloudmart.wish.service.AccountDeletionService;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.service.DataExportService;
import com.cloudmart.wish.vo.ConsentStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一隐私与数据控制入口（N05，/api/wish/v2/my/privacy）。
 *
 * <p>聚合既有能力（授权/导出/注销/默认关闭项）为一个只读视图：明确"谁能看到"、
 * "撤回后影响什么"、"导出进度"、"注销阶段"。不新造第四套开关——AI 授权复用
 * consent，分享授权按每条还愿保存，位置共享默认关闭（开关随 B16/N05 后续持久化）。</p>
 */
@RestController
@RequestMapping("/v2/my")
@Tag(name = "心愿宇宙·隐私中心（用户）", description = "统一隐私与数据控制视图（N05）")
@RequiredArgsConstructor
public class PrivacyCenterController {

    private final ConsentService consentService;
    private final DataExportService dataExportService;
    private final AccountDeletionService accountDeletionService;

    @GetMapping("/privacy")
    @Operation(summary = "隐私中心聚合视图", description = "AI 授权、导出进度、注销阶段、"
            + "默认关闭项（位置共享/社区自动传播）一览；各端开关数据源")
    public ApiResponse<Map<String, Object>> privacy(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {

        Map<String, Object> view = new LinkedHashMap<>();

        // AI 数据处理授权（撤回后新的外部 AI 调用即停止）
        ConsentStatusVO aiConsent = consentService.getConsentStatus(userId,
                ConsentType.AI_DATA_PROCESSING);
        view.put("aiDataProcessing", Map.of(
                "granted", aiConsent != null && aiConsent.granted(),
                "version", aiConsent == null || aiConsent.version() == null ? "" : aiConsent.version(),
                "updatedAt", aiConsent == null || aiConsent.updatedAt() == null ? "" : aiConsent.updatedAt()));

        // 数据导出进度（最近一条任务）
        List<com.cloudmart.wish.entity.DataExport> exports = dataExportService.listTasks(userId);
        Map<String, Object> export = new LinkedHashMap<>();
        if (exports.isEmpty()) {
            export.put("status", "NONE");
        } else {
            com.cloudmart.wish.entity.DataExport latest = exports.get(0);
            export.put("taskId", latest.getId());
            export.put("status", latest.getStatus());
            export.put("expiresAt", latest.getExpiresAt());
        }
        view.put("dataExport", export);

        // 注销阶段（PENDING/EXECUTING/EXECUTED/CANCELED 或 NONE）
        com.cloudmart.wish.entity.WishAccountDeletion deletion = accountDeletionService.getStatus(userId);
        Map<String, Object> deletionView = new LinkedHashMap<>();
        if (deletion == null) {
            deletionView.put("status", "NONE");
        } else {
            deletionView.put("status", deletion.getStatus());
            deletionView.put("executeAfter", deletion.getExecuteAfter());
            deletionView.put("executedAt", deletion.getExecutedAt());
        }
        view.put("accountDeletion", deletionView);

        // 默认关闭项（任务书 N05：默认关闭位置共享与社区自动传播）
        view.put("defaults", Map.of(
                "locationSharing", false,
                "fulfillmentAutoShare", false,
                "rmbPayment", false));

        return ApiResponse.ok(view);
    }
}
