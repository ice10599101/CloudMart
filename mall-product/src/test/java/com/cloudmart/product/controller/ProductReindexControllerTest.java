package com.cloudmart.product.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.es.IndexManager;
import com.cloudmart.product.service.ProductSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T08 版本驱动投影：索引管理端点——蓝绿重建编排、别名状态与版本清单。
 */
class ProductReindexControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductSyncService productSyncService = Mockito.mock(ProductSyncService.class);
    private final IndexManager indexManager = Mockito.mock(IndexManager.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductReindexController(productSyncService, indexManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /products/es/reindex - 全量重建数据")
    class ReindexAll {

        @Test
        @DisplayName("全量重建数据成功（别名路由当前写索引）")
        void shouldReindexAll() throws Exception {
            given(productSyncService.reindexAll()).willReturn(100);

            mockMvc.perform(post("/products/es/reindex"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(100));
        }
    }

    @Nested
    @DisplayName("POST /products/es/sync/{productId} - 单商品同步")
    class SyncProduct {

        @Test
        @DisplayName("单商品同步成功")
        void shouldSyncProduct() throws Exception {
            willDoNothing().given(productSyncService).syncToEs(1L);

            mockMvc.perform(post("/products/es/sync/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /products/es/index/status - 查询索引状态")
    class IndexStatus {

        @Test
        @DisplayName("别名存在时应返回写索引与 mapping")
        void shouldReturnStatusWhenIndexExists() throws Exception {
            Map<String, Object> mapping = new HashMap<>();
            mapping.put("properties", Map.of("name", Map.of("type", "text")));
            Map<String, Object> settings = new HashMap<>();
            settings.put("number_of_shards", "1");

            given(indexManager.indexExists()).willReturn(true);
            given(indexManager.currentWriteIndex()).willReturn(Optional.of("products_v2"));
            given(indexManager.legacyIndexExists()).willReturn(false);
            given(indexManager.getIndexMapping()).willReturn(mapping);
            given(indexManager.getIndexSettings()).willReturn(settings);

            mockMvc.perform(get("/products/es/index/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.aliasExists").value(true))
                    .andExpect(jsonPath("$.data.writeIndex").value("products_v2"))
                    .andExpect(jsonPath("$.data.mapping.properties.name.type").value("text"));
        }

        @Test
        @DisplayName("别名不存在时应返回 aliasExists=false")
        void shouldReturnNotExistsWhenIndexMissing() throws Exception {
            given(indexManager.indexExists()).willReturn(false);
            given(indexManager.legacyIndexExists()).willReturn(false);
            given(indexManager.currentWriteIndex()).willReturn(Optional.empty());

            mockMvc.perform(get("/products/es/index/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.aliasExists").value(false));
        }
    }

    @Nested
    @DisplayName("POST /products/es/index/full-rebuild - 蓝绿全量重建")
    class FullRebuild {

        @Test
        @DisplayName("常规蓝绿重建：建 v{n+1} → 写入 → 原子切换")
        void shouldFullRebuild() throws Exception {
            given(indexManager.indexExists()).willReturn(true);
            given(indexManager.nextVersionIndexName()).willReturn("products_v3");
            given(indexManager.createVersionedIndex("products_v3")).willReturn(true);
            given(productSyncService.reindexAll("products_v3")).willReturn(50);
            given(indexManager.currentWriteIndex()).willReturn(Optional.of("products_v3"));
            given(indexManager.switchAliasTo("products_v3")).willReturn(true);

            mockMvc.perform(post("/products/es/index/full-rebuild"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.targetIndex").value("products_v3"))
                    .andExpect(jsonPath("$.data.documentsSynced").value(50))
                    .andExpect(jsonPath("$.data.aliasSwitched").value(true));
        }

        @Test
        @DisplayName("版本索引创建失败时不同步数据，返回业务错误")
        void shouldNotSyncWhenCreateFailed() throws Exception {
            given(indexManager.indexExists()).willReturn(true);
            given(indexManager.nextVersionIndexName()).willReturn("products_v3");
            given(indexManager.createVersionedIndex("products_v3")).willReturn(false);

            mockMvc.perform(post("/products/es/index/full-rebuild"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("ES_INDEX_CREATE_FAILED"));
            Mockito.verify(productSyncService, Mockito.never()).reindexAll(anyString());
        }
    }

    @Nested
    @DisplayName("GET /products/es/index/versions - 版本清单")
    class IndexVersions {

        @Test
        @DisplayName("返回当前写索引与别名指向")
        void shouldReturnVersions() throws Exception {
            given(indexManager.currentWriteIndex()).willReturn(Optional.of("products_v2"));
            given(indexManager.legacyIndexExists()).willReturn(false);

            mockMvc.perform(get("/products/es/index/versions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.writeIndex").value("products_v2"));
        }
    }

    @Nested
    @DisplayName("DELETE /products/es/index - 清理")
    class DeleteIndex {

        @Test
        @DisplayName("破坏性删除已移除：返回引导信息而非删除")
        void shouldNotDelete() throws Exception {
            given(indexManager.currentWriteIndex()).willReturn(Optional.of("products_v2"));

            mockMvc.perform(delete("/products/es/index"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.removed").value(false));
            Mockito.verifyNoInteractions(productSyncService);
        }
    }
}
