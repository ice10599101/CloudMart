package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.AiFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务管理-AI 域：商品向量同步触发与删除。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：AI 向量同步")
public class AdminAiController {

    private final AiFeignClient aiFeignClient;

    public AdminAiController(AiFeignClient aiFeignClient) {
        this.aiFeignClient = aiFeignClient;
    }

    // ==================== AI 管理 ====================

    @PostMapping("/ai/vector-sync/full")
    @OperLog(title = "AI管理", businessType = 1)
    @RequiresPermission("business:ai:sync")
    @Operation(summary = "全量向量同步", description = "触发全量商品向量同步到ES")
    public ApiResponse<Void> triggerFullVectorSync() {
        return aiFeignClient.triggerFullSync();
    }

    @PostMapping("/ai/vector-sync/product/{productId}")
    @OperLog(title = "AI管理", businessType = 1)
    @RequiresPermission("business:ai:sync")
    @Operation(summary = "增量向量同步", description = "同步单个商品向量到ES")
    public ApiResponse<Void> syncProductVector(@PathVariable Long productId) {
        return aiFeignClient.syncProduct(productId);
    }

    @DeleteMapping("/ai/vector-sync/product/{productId}")
    @OperLog(title = "AI管理", businessType = 3)
    @RequiresPermission("business:ai:sync")
    @Operation(summary = "删除商品向量", description = "从ES中删除商品向量")
    public ApiResponse<Void> deleteProductVector(@PathVariable Long productId) {
        return aiFeignClient.deleteProductVector(productId);
    }
}
