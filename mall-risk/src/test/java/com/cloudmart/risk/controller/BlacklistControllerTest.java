package com.cloudmart.risk.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.risk.entity.BlacklistEntry;
import com.cloudmart.risk.service.BlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BlacklistControllerTest {

    private MockMvc mockMvc;

    private final BlacklistService blacklistService = Mockito.mock(BlacklistService.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BlacklistController(blacklistService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("添加黑名单 - 成功返回信封")
    void addToBlacklist_ShouldReturnEnvelope() throws Exception {
        BlacklistEntry entry = new BlacklistEntry();
        entry.setId(1L);
        entry.setTargetType("USER");
        entry.setTargetValue("100");
        entry.setReason("恶意刷单");
        entry.setCreatedAt(FIXED_TIME);

        given(blacklistService.addToBlacklist("USER", "100", "恶意刷单", null)).willReturn(entry);

        mockMvc.perform(post("/blacklist")
                        .param("type", "USER")
                        .param("value", "100")
                        .param("reason", "恶意刷单"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.targetType").value("USER"))
                .andExpect(jsonPath("$.data.reason").value("恶意刷单"));
    }

    @Test
    @DisplayName("移除黑名单 - 成功返回信封")
    void removeFromBlacklist_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/blacklist/USER/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(blacklistService).removeFromBlacklist("USER", "100");
    }

    @Test
    @DisplayName("检查是否在黑名单 - 在黑名单中返回true")
    void checkBlacklist_WhenBlacklisted_ShouldReturnTrue() throws Exception {
        given(blacklistService.isBlacklisted("USER", "100")).willReturn(true);

        mockMvc.perform(get("/blacklist/check")
                        .param("type", "USER")
                        .param("value", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("检查是否在黑名单 - 不在黑名单中返回false")
    void checkBlacklist_WhenNotBlacklisted_ShouldReturnFalse() throws Exception {
        given(blacklistService.isBlacklisted("USER", "200")).willReturn(false);

        mockMvc.perform(get("/blacklist/check")
                        .param("type", "USER")
                        .param("value", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("黑名单列表 - 成功返回分页信封")
    void listBlacklist_ShouldReturnPagedEnvelope() throws Exception {
        BlacklistEntry entry = new BlacklistEntry();
        entry.setId(1L);
        entry.setTargetType("IP");
        entry.setTargetValue("192.168.1.1");
        entry.setReason("异常访问");
        entry.setCreatedAt(FIXED_TIME);

        Page<BlacklistEntry> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(entry));

        given(blacklistService.listBlacklist(null, 1, 20)).willReturn(page);

        mockMvc.perform(get("/blacklist/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("黑名单列表 - 按类型筛选返回信封")
    void listBlacklist_WithTypeFilter_ShouldReturnEnvelope() throws Exception {
        Page<BlacklistEntry> page = new Page<>(1, 20, 0L);
        page.setRecords(List.of());

        given(blacklistService.listBlacklist("IP", 1, 20)).willReturn(page);

        mockMvc.perform(get("/blacklist/list")
                        .param("type", "IP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
