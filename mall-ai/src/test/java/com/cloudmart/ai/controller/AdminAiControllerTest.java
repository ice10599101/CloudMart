package com.cloudmart.ai.controller;

import com.cloudmart.ai.service.ProductVectorSyncService;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAiControllerTest {

    private MockMvc mockMvc;

    private final ProductVectorSyncService vectorSyncService = Mockito.mock(ProductVectorSyncService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAiController(vectorSyncService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("触发全量向量同步 - 成功返回信封")
    void triggerFullSync_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/admin/vector-sync/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("started"))
                .andExpect(jsonPath("$.data.message").value("全量同步已启动"));

        verify(vectorSyncService).fullSync();
    }

    @Test
    @DisplayName("增量同步单个商品向量 - 成功返回信封")
    void syncProduct_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/admin/vector-sync/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.productId").value("1"));

        verify(vectorSyncService).syncProduct(1L);
    }

    @Test
    @DisplayName("删除商品向量 - 成功返回信封")
    void deleteProductVector_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/admin/vector-sync/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("deleted"))
                .andExpect(jsonPath("$.data.productId").value("1"));

        verify(vectorSyncService).deleteProduct(1L);
    }
}
