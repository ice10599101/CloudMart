package com.cloudmart.admin.controller;

import com.cloudmart.admin.service.AdminMonitorService;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminMonitorControllerTest {

    private MockMvc mockMvc;
    private AdminMonitorService adminMonitorService;

    @BeforeEach
    void setUp() {
        adminMonitorService = mock(AdminMonitorService.class);
        AdminMonitorController controller = new AdminMonitorController(adminMonitorService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /monitor/server - 服务器信息")
    class ServerInfoTests {

        @Test
        @DisplayName("返回服务器监控信息")
        void serverInfo_returnsServerInfo() throws Exception {
            Map<String, Object> serverInfo = Map.of(
                    "cpu", Map.of("cpuNum", 8, "usage", 25.5),
                    "mem", Map.of("total", 16777216, "used", 8388608, "usage", 50.0),
                    "jvm", Map.of("name", "OpenJDK", "version", "26")
            );
            given(adminMonitorService.getServerInfo()).willReturn(serverInfo);

            mockMvc.perform(get("/monitor/server"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.cpu.cpuNum").value(8))
                    .andExpect(jsonPath("$.data.jvm.name").value("OpenJDK"));

            verify(adminMonitorService).getServerInfo();
        }
    }

    @Nested
    @DisplayName("GET /monitor/cache - 缓存信息")
    class CacheInfoTests {

        @Test
        @DisplayName("返回Redis缓存监控信息")
        void cacheInfo_returnsCacheInfo() throws Exception {
            Map<String, Object> cacheInfo = Map.of(
                    "redisVersion", "9.0.0",
                    "usedMemoryHuman", "1.5M",
                    "dbSize", 42,
                    "hitRate", 85.5
            );
            given(adminMonitorService.getCacheInfo()).willReturn(cacheInfo);

            mockMvc.perform(get("/monitor/cache"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.redisVersion").value("9.0.0"))
                    .andExpect(jsonPath("$.data.dbSize").value(42));

            verify(adminMonitorService).getCacheInfo();
        }
    }
}
