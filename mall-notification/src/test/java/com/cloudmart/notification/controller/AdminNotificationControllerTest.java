package com.cloudmart.notification.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.notification.converter.NotificationConverter;
import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.service.NotificationService;
import com.cloudmart.notification.vo.NotificationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminNotificationControllerTest {

    private MockMvc mockMvc;

    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final NotificationConverter notificationConverter = Mockito.mock(NotificationConverter.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminNotificationController(notificationService, notificationConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("查询通知列表 - 成功返回信封")
    void listNotifications_ShouldReturnEnvelope() throws Exception {
        NotificationDTO dto = new NotificationDTO(1L, 1L, "SYSTEM", "系统通知",
                "系统维护通知", true, null, null, FIXED_TIME);
        NotificationVO vo = new NotificationVO(1L, "SYSTEM", "系统通知",
                "系统维护通知", true, null, null, FIXED_TIME);

        given(notificationService.listAllNotifications(null, null, 1, 20)).willReturn(List.of(dto));
        given(notificationConverter.dtoListToVOList(List.of(dto))).willReturn(List.of(vo));

        mockMvc.perform(get("/admin/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].type").value("SYSTEM"));
    }

    @Test
    @DisplayName("发送通知 - 成功返回信封")
    void sendNotification_ShouldReturnEnvelope() throws Exception {
        NotificationDTO dto = new NotificationDTO(1L, 1L, "LIKE", "收到点赞",
                "用户A赞了你的商品", false, 100L, "PRODUCT", FIXED_TIME);
        NotificationVO vo = new NotificationVO(1L, "LIKE", "收到点赞",
                "用户A赞了你的商品", false, 100L, "PRODUCT", FIXED_TIME);

        given(notificationService.sendNotification(Mockito.any())).willReturn(dto);
        given(notificationConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/admin/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"type\":\"LIKE\",\"title\":\"收到点赞\",\"content\":\"用户A赞了你的商品\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.type").value("LIKE"));
    }

    @Test
    @DisplayName("发送通知 - 缺少必填字段返回校验错误")
    void sendNotification_WhenMissingRequiredField_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/admin/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("发送用户通知 - 成功返回信封")
    void sendNotificationToUser_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/admin/notifications/user")
                        .param("userId", "1")
                        .param("type", "SYSTEM")
                        .param("title", "系统通知")
                        .param("content", "系统维护"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).sendNotificationToUser(1L, "SYSTEM", "系统通知", "系统维护", null, null);
    }

    @Test
    @DisplayName("广播通知 - 成功返回信封")
    void broadcastNotification_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/admin/notifications/broadcast")
                        .param("type", "SYSTEM")
                        .param("title", "系统公告")
                        .param("content", "系统升级通知"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).broadcastNotification("SYSTEM", "系统公告", "系统升级通知");
    }
}
