package com.cloudmart.product.controller;

import com.cloudmart.common.api.ApiResponse;
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

import java.util.Map;

@RestController
@RequestMapping("/products/es")
@Tag(name = "商品搜索索引管理", description = "ES索引重建、同步与索引结构管理")
@ConditionalOnBean(ProductSyncService.class)
public class ProductReindexController {

    private final ProductSyncService productSyncService;
    private final IndexManager indexManager;

    public ProductReindexController(ProductSyncService productSyncService, IndexManager indexManager) {
        this.productSyncService = productSyncService;
        this.indexManager = indexManager;
    }

    @PostMapping("/reindex")
    @Operation(summary = "全量重建数据", description = "将MySQL中所有商品全量同步到Elasticsearch（不重建索引结构）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Integer> reindexAll() {
        int count = productSyncService.reindexAll();
        return ApiResponse.ok(count);
    }

    @PostMapping("/sync/{productId}")
    @Operation(summary = "单商品同步", description = "将指定商品同步到Elasticsearch")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> syncProduct(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        productSyncService.syncToEs(productId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/index/status")
    @Operation(summary = "查询索引状态", description = "检查 products 索引是否存在，并返回 mapping 与 settings")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Map<String, Object>> indexStatus() {
        boolean exists = indexManager.indexExists();
        Map<String, Object> info = Map.of(
                "exists", exists,
                "mapping", exists ? indexManager.getIndexMapping() : Map.of(),
                "settings", exists ? indexManager.getIndexSettings() : Map.of()
        );
        return ApiResponse.ok(info);
    }

    @PostMapping("/index/recreate")
    @Operation(summary = "重建索引结构", description = "删除现有索引并按JSON定义重新创建（含mapping），不自动同步数据")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Boolean> recreateIndex() {
        boolean success = indexManager.recreateIndex();
        return ApiResponse.ok(success);
    }

    @PostMapping("/index/full-rebuild")
    @Operation(summary = "完全重建", description = "删除索引→按JSON定义重建索引结构→全量同步MySQL数据到ES")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Map<String, Object>> fullRebuild() {
        boolean recreated = indexManager.recreateIndex();
        int count = recreated ? productSyncService.reindexAll() : 0;
        return ApiResponse.ok(Map.of(
                "indexRecreated", recreated,
                "documentsSynced", count
        ));
    }

    @DeleteMapping("/index")
    @Operation(summary = "删除索引", description = "删除 products 索引（谨慎操作，数据将丢失）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Boolean> deleteIndex() {
        boolean success = indexManager.deleteIndex();
        return ApiResponse.ok(success);
    }
}
