package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminConfigRequest;
import com.cloudmart.admin.dto.AdminConfigResponse;
import com.cloudmart.admin.service.AdminConfigService;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminConfigControllerTest {

    private MockMvc mockMvc;
    private AdminConfigService adminConfigService;

    @BeforeEach
    void setUp() {
        adminConfigService = mock(AdminConfigService.class);
        AdminConfigController controller = new AdminConfigController(adminConfigService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsAllConfigs() throws Exception {
        AdminConfigResponse config = new AdminConfigResponse(1L, "系统标题", "sys.index.title", "CloudMart", 0, null, LocalDateTime.now());
        given(adminConfigService.list()).willReturn(List.of(config));

        mockMvc.perform(get("/configs").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].configKey").value("sys.index.title"));
    }

    @Test
    void getById_returnsConfigDetail() throws Exception {
        AdminConfigResponse config = new AdminConfigResponse(1L, "系统标题", "sys.index.title", "CloudMart", 0, null, LocalDateTime.now());
        given(adminConfigService.getById(1L)).willReturn(config);

        mockMvc.perform(get("/configs/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getByKey_returnsConfigByKey() throws Exception {
        AdminConfigResponse config = new AdminConfigResponse(1L, "系统标题", "sys.index.title", "CloudMart", 0, null, LocalDateTime.now());
        given(adminConfigService.getByKey("sys.index.title")).willReturn(config);

        mockMvc.perform(get("/configs/key/sys.index.title").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.configKey").value("sys.index.title"));
    }

    @Test
    void create_configCreatedSuccessfully() throws Exception {
        doNothing().when(adminConfigService).create(any(AdminConfigRequest.class));

        mockMvc.perform(post("/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configName\":\"测试配置\",\"configKey\":\"sys.test\",\"configValue\":\"value\",\"configType\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_configUpdatedSuccessfully() throws Exception {
        doNothing().when(adminConfigService).update(anyLong(), any(AdminConfigRequest.class));

        mockMvc.perform(put("/configs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configName\":\"测试配置\",\"configKey\":\"sys.test\",\"configValue\":\"updated\",\"configType\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_configDeletedSuccessfully() throws Exception {
        doNothing().when(adminConfigService).delete(1L);

        mockMvc.perform(delete("/configs/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void refreshCache_cacheRefreshedSuccessfully() throws Exception {
        doNothing().when(adminConfigService).refreshCache();

        mockMvc.perform(put("/configs/cache/refresh").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
