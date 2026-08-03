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

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        @DisplayName("全量重建数据成功")
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
        @DisplayName("索引存在时应返回 mapping 与 settings")
        void shouldReturnStatusWhenIndexExists() throws Exception {
            Map<String, Object> mapping = new HashMap<>();
            mapping.put("properties", Map.of("name", Map.of("type", "text")));
            Map<String, Object> settings = new HashMap<>();
            settings.put("number_of_shards", "1");

            given(indexManager.indexExists()).willReturn(true);
            given(indexManager.getIndexMapping()).willReturn(mapping);
            given(indexManager.getIndexSettings()).willReturn(settings);

            mockMvc.perform(get("/products/es/index/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.exists").value(true))
                    .andExpect(jsonPath("$.data.mapping.properties.name.type").value("text"));
        }

        @Test
        @DisplayName("索引不存在时应返回 exists=false")
        void shouldReturnNotExistsWhenIndexMissing() throws Exception {
            given(indexManager.indexExists()).willReturn(false);

            mockMvc.perform(get("/products/es/index/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.exists").value(false));
        }
    }

    @Nested
    @DisplayName("POST /products/es/index/recreate - 重建索引结构")
    class RecreateIndex {

        @Test
        @DisplayName("重建索引结构成功")
        void shouldRecreateIndex() throws Exception {
            given(indexManager.recreateIndex()).willReturn(true);

            mockMvc.perform(post("/products/es/index/recreate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true));
        }
    }

    @Nested
    @DisplayName("POST /products/es/index/full-rebuild - 完全重建")
    class FullRebuild {

        @Test
        @DisplayName("完全重建应先重建索引结构再同步数据")
        void shouldFullRebuild() throws Exception {
            given(indexManager.recreateIndex()).willReturn(true);
            given(productSyncService.reindexAll()).willReturn(50);

            mockMvc.perform(post("/products/es/index/full-rebuild"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.indexRecreated").value(true))
                    .andExpect(jsonPath("$.data.documentsSynced").value(50));
        }

        @Test
        @DisplayName("索引重建失败时不应同步数据")
        void shouldNotSyncWhenRecreateFailed() throws Exception {
            given(indexManager.recreateIndex()).willReturn(false);

            mockMvc.perform(post("/products/es/index/full-rebuild"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.indexRecreated").value(false))
                    .andExpect(jsonPath("$.data.documentsSynced").value(0));
        }
    }

    @Nested
    @DisplayName("DELETE /products/es/index - 删除索引")
    class DeleteIndex {

        @Test
        @DisplayName("删除索引成功")
        void shouldDeleteIndex() throws Exception {
            given(indexManager.deleteIndex()).willReturn(true);

            mockMvc.perform(delete("/products/es/index"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true));
        }
    }
}
