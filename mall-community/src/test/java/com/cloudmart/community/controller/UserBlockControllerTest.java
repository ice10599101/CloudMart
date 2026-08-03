package com.cloudmart.community.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserBlockControllerTest {

    private MockMvc mockMvc;

    private final UserBlockService userBlockService = Mockito.mock(UserBlockService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserBlockController(userBlockService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /blocks/{userId} - 拉黑用户成功")
    void blockUser_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(userBlockService).blockUser(1L, 2L);

        mockMvc.perform(post("/blocks/2")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /blocks/{userId} - 缺少X-User-Id头返回401")
    void blockUser_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/blocks/2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("DELETE /blocks/{userId} - 取消拉黑成功")
    void unblockUser_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(userBlockService).unblockUser(1L, 2L);

        mockMvc.perform(delete("/blocks/2")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /blocks/{userId} - 缺少X-User-Id头返回401")
    void unblockUser_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(delete("/blocks/2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /blocks - 获取拉黑列表成功")
    void getBlockedUsers_ShouldReturnSuccess() throws Exception {
        given(userBlockService.getBlockedUserIds(1L)).willReturn(List.of(2L, 3L));

        mockMvc.perform(get("/blocks")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value(2))
                .andExpect(jsonPath("$.data[1]").value(3));
    }

    @Test
    @DisplayName("GET /blocks - 缺少X-User-Id头返回401")
    void getBlockedUsers_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/blocks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /blocks/check - 已拉黑返回blocked=true")
    void checkBlockStatus_Blocked_ShouldReturnTrue() throws Exception {
        given(userBlockService.isBlocked(1L, 2L)).willReturn(true);

        mockMvc.perform(get("/blocks/check")
                        .header(USER_ID_HEADER, 1)
                        .param("targetUserId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.blocked").value(true));
    }

    @Test
    @DisplayName("GET /blocks/check - 未拉黑返回blocked=false")
    void checkBlockStatus_NotBlocked_ShouldReturnFalse() throws Exception {
        given(userBlockService.isBlocked(1L, 2L)).willReturn(false);

        mockMvc.perform(get("/blocks/check")
                        .header(USER_ID_HEADER, 1)
                        .param("targetUserId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.blocked").value(false));
    }
}
