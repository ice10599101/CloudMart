package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminFileAssetPage;
import com.cloudmart.admin.feign.FileFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADMIN-04：管理员文件资产代理契约——分页参数收敛（page≥1、size≤100）、
 * 过滤条件透传、删除代理。
 */
@DisplayName("AdminFileController 文件资产代理")
class AdminFileControllerTest {

    private MockMvc mockMvc;
    private FileFeignClient fileFeignClient;

    @BeforeEach
    void setUp() {
        fileFeignClient = mock(FileFeignClient.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminFileController(fileFeignClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("列表：非法分页收敛为 page=1/size=100，purpose 透传")
    void pageAssets_sanitizesPaging() throws Exception {
        given(fileFeignClient.pageAdminAssets(1, 100, null, "GIFT_IMAGE"))
                .willReturn(ApiResponse.ok(new AdminFileAssetPage(List.of(), 0, 1, 100)));

        mockMvc.perform(get("/file/assets")
                        .param("page", "0")
                        .param("size", "500")
                        .param("purpose", "GIFT_IMAGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(fileFeignClient).pageAdminAssets(1, 100, null, "GIFT_IMAGE");
    }

    @Test
    @DisplayName("删除：按 fileId 转发（引用保护由文件服务裁决）")
    void delete_forwards() throws Exception {
        given(fileFeignClient.deleteAdminAsset(7L)).willReturn(ApiResponse.ok(null));

        mockMvc.perform(delete("/file/assets/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(fileFeignClient).deleteAdminAsset(7L);
    }
}
