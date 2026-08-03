package com.cloudmart.community.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.UserSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserSettingControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UserSettingService userSettingService = Mockito.mock(UserSettingService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserSettingController(userSettingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /settings - 获取用户设置成功")
    void getUserSettings_ShouldReturnSuccess() throws Exception {
        Map<String, String> settings = Map.of(
                "notification_enabled", "true",
                "privacy_level", "public",
                "comment_notification", "true");
        given(userSettingService.getUserSettings(1L)).willReturn(settings);

        mockMvc.perform(get("/settings")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.notification_enabled").value("true"))
                .andExpect(jsonPath("$.data.privacy_level").value("public"));
    }

    @Test
    @DisplayName("GET /settings - 缺少X-User-Id头返回401")
    void getUserSettings_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PUT /settings - 更新用户设置成功")
    void updateUserSettings_ShouldReturnSuccess() throws Exception {
        Map<String, String> settings = Map.of(
                "notification_enabled", "false",
                "privacy_level", "private");
        willDoNothing().given(userSettingService).updateUserSettings(1L, settings);

        mockMvc.perform(put("/settings")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settings)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /settings - 缺少X-User-Id头返回401")
    void updateUserSettings_WithoutUserId_ShouldReturn401() throws Exception {
        Map<String, String> settings = Map.of("notification_enabled", "false");

        mockMvc.perform(put("/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settings)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
