package com.cloudmart.product.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.es.IndexManager;
import com.cloudmart.product.service.ProductSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/products/es")
@Tag(name = "商品搜索索引管理", description = "T08 版本驱动投影：蓝绿重建、原子别名切换与版本管理")
@ConditionalOnBean(ProductSyncService.class)
// SEC-01：索引重建属运维动作，仅服务身份（mall-admin/mall-ai 的服务令牌）可调用
@PreAuthorize("hasRole('INTERNAL')")
public class ProductReindexController {

    private final ProductSyncService productSyncService;
    private final IndexManager indexManager;

    public ProductReindexController(ProductSyncService productSyncService, IndexManager indexManager) {
        this.productSyncService = productSyncService;
        this.indexManager = indexManager;
    }

    @PostMapping("/reindex")
    @Operation(summary = "全量重建数据", description = "将MySQL中所有商品全量同步到当前索引（别名路由，不改变索引结构）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Integer> reindexAll() {
        return ApiResponse.ok(productSyncService.reindexAll());
    }

    @PostMapping("/sync/{productId}")
    @Operation(summary = "单商品同步", description = "将指定商品同步到Elasticsearch（别名路由当前写索引）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> syncProduct(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        productSyncService.syncToEs(productId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/index/status")
    @Operation(summary = "查询索引状态", description = "别名指向（含写索引）、mapping 与 settings")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Map<String, Object>> indexStatus() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("aliasExists", indexManager.indexExists());
        info.put("legacyPhysicalIndexExists", indexManager.legacyIndexExists());
        info.put("writeIndex", indexManager.currentWriteIndex().orElse(null));
        info.put("aliasTargets", indexManager.aliasTargets());
        if (indexManager.indexExists()) {
            info.put("mapping", indexManager.getIndexMapping());
            info.put("settings", indexManager.getIndexSettings());
        }
        return ApiResponse.ok(info);
    }

    /**
     * T08 蓝绿全量重建（零停机，除首次遗留迁移外）：
     * ① 建下一版本实体索引（products_v{n+1}，新 settings/mapping）；
     * ② 全量 MySQL → 新索引（此时读仍走别名=旧版本，搜索不受影响）；
     * ③ 原子切换别名到新索引；旧版本保留可回滚。
     * 首次迁移存在与历史同名实体索引的切换：删除遗留索引与建别名之间为毫秒级窗口。
     */
    @PostMapping("/index/full-rebuild")
    @Operation(summary = "蓝绿全量重建", description = "建新版本索引→写入→原子切换别名；旧版本保留可回滚")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Map<String, Object>> fullRebuild() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean legacyRemoved = false;

        String targetIndex = indexManager.nextVersionIndexName();
        boolean fromLegacy = !indexManager.indexExists() && indexManager.legacyIndexExists();
        boolean bootstrapping = !indexManager.indexExists() && !indexManager.legacyIndexExists();

        if (!indexManager.createVersionedIndex(targetIndex)) {
            throw new BusinessException("ES_INDEX_CREATE_FAILED", "创建版本索引失败: " + targetIndex);
        }

        // 写入新版本期间，读流量仍由别名路由到旧版本（蓝绿窗口）
        int count = productSyncService.reindexAll(targetIndex);

        if (fromLegacy) {
            // ES 不允许别名与同名实体索引共存：一次性迁移窗口（毫秒级）
            legacyRemoved = indexManager.deleteLegacyIndex();
        }
        boolean aliasSwitched = indexManager.switchAliasTo(targetIndex);
        if (!aliasSwitched) {
            throw new BusinessException("ES_ALIAS_SWITCH_FAILED",
                    "别名切换失败: " + targetIndex + "（新索引已写入 " + count + " 条，可重试切换）");
        }

        result.put("targetIndex", targetIndex);
        result.put("previousIndex", indexManager.currentWriteIndex()
                .filter(idx -> !idx.equals(targetIndex)).orElse(null));
        result.put("legacyPhysicalIndexRemoved", legacyRemoved);
        result.put("bootstrapping", bootstrapping);
        result.put("documentsSynced", count);
        result.put("aliasSwitched", aliasSwitched);
        return ApiResponse.ok(result);
    }

    @GetMapping("/index/versions")
    @Operation(summary = "版本清单", description = "别名当前指向（写索引）与版本化实体索引")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Map<String, Object>> indexVersions() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("writeIndex", indexManager.currentWriteIndex().orElse(null));
        info.put("aliasTargets", indexManager.aliasTargets().keySet());
        info.put("legacyPhysicalIndexExists", indexManager.legacyIndexExists());
        return ApiResponse.ok(info);
    }

    @DeleteMapping("/index")
    @Operation(summary = "清理", description = "T08 已移除破坏性删除——重建经蓝绿切换完成，旧版本保留回滚能力")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Map<String, Object>> deleteIndex() {
        Map<String, Object> info = Map.of(
                "removed", false,
                "reason", "破坏性删除已移除：使用 /index/full-rebuild 蓝绿重建，旧版本索引保留可回滚",
                "writeIndex", indexManager.currentWriteIndex().orElse(null)
        );
        return ApiResponse.ok(info);
    }
}
