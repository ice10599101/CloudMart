package com.cloudmart.ai.controller;

import com.cloudmart.ai.service.ProductVectorSyncService;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "AI 管理接口", description = "AI 向量索引管理与数据同步")
@RestController
@RequestMapping("/admin")
@ConditionalOnProperty(name = "ai.vector.enabled", havingValue = "true")
public class AdminAiController {

    private final ProductVectorSyncService vectorSyncService;

    public AdminAiController(ProductVectorSyncService vectorSyncService) {
        this.vectorSyncService = vectorSyncService;
    }

    @Operation(summary = "触发全量向量同步", description = "将所有商品数据重新生成 Embedding 并写入 ES 向量索引")
    @PostMapping("/vector-sync/full")
    public ApiResponse<Map<String, String>> triggerFullSync() {
        vectorSyncService.fullSync();
        return ApiResponse.ok(Map.of("status", "started", "message", "全量同步已启动"));
    }

    @Operation(summary = "增量同步单个商品向量", description = "将指定商品重新生成 Embedding 并更新 ES 向量索引")
    @PostMapping("/vector-sync/product/{productId}")
    public ApiResponse<Map<String, String>> syncProduct(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        vectorSyncService.syncProduct(productId);
        return ApiResponse.ok(Map.of("status", "completed", "productId", productId.toString()));
    }

    @Operation(summary = "删除商品向量", description = "从 ES 向量索引中删除指定商品")
    @DeleteMapping("/vector-sync/product/{productId}")
    public ApiResponse<Map<String, String>> deleteProductVector(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        vectorSyncService.deleteProduct(productId);
        return ApiResponse.ok(Map.of("status", "deleted", "productId", productId.toString()));
    }
}
