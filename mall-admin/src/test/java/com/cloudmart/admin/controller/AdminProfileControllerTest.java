package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminPasswordUpdateRequest;
import com.cloudmart.admin.dto.AdminProfileResponse;
import com.cloudmart.admin.service.AdminProfileService;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminProfileControllerTest {

    private MockMvc mockMvc;
    private AdminProfileService adminProfileService;

    @BeforeEach
    void setUp() {
        adminProfileService = mock(AdminProfileService.class);
        AdminProfileController controller = new AdminProfileController(adminProfileService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @BeforeEach
    void setSecurityContext() {
        AdminSecurityContext.set(new AdminSecurityContext(
                1L, "admin", "admin", java.util.Set.of("*:*:*"), 1L
        ));
    }

    @AfterEach
    void clearSecurityContext() {
        AdminSecurityContext.clear();
    }

    @Test
    void getProfile_returnsProfileInfo() throws Exception {
        AdminProfileResponse profile = new AdminProfileResponse(
                1L, "admin", "Admin", "admin@test.com", "13800000001", null, 1L, 0, LocalDateTime.now(), Set.of("*:*:*"), Set.of("admin")
        );
        given(adminProfileService.getProfile(1L)).willReturn(profile);

        mockMvc.perform(get("/profile").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.nickname").value("Admin"));
    }

    @Test
    void getProfile_withoutAuth_returnsUnauthorized() throws Exception {
        AdminSecurityContext.clear();

        mockMvc.perform(get("/profile").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void updateProfile_updatedSuccessfully() throws Exception {
        doNothing().when(adminProfileService).updateProfile(anyLong(), anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(put("/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"NewNick\",\"email\":\"new@test.com\",\"phone\":\"13800000002\",\"avatar\":\"avatar.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateProfile_withoutAuth_returnsUnauthorized() throws Exception {
        AdminSecurityContext.clear();

        mockMvc.perform(put("/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"NewNick\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void updatePassword_passwordChangedSuccessfully() throws Exception {
        doNothing().when(adminProfileService).updatePassword(anyLong(), anyString(), anyString());

        mockMvc.perform(put("/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"oldPass\",\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updatePassword_withoutAuth_returnsUnauthorized() throws Exception {
        AdminSecurityContext.clear();

        mockMvc.perform(put("/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"oldPass123\",\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
