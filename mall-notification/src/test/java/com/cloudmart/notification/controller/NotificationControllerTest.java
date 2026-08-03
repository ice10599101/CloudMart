package com.cloudmart.notification.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.notification.converter.NotificationConverter;
import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.dto.UnreadCountDTO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    private MockMvc mockMvc;

    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final NotificationConverter notificationConverter = Mockito.mock(NotificationConverter.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService, notificationConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("通知列表 - 成功返回信封")
    void listNotifications_ShouldReturnEnvelope() throws Exception {
        NotificationDTO dto = new NotificationDTO(1L, 1L, "LIKE", "收到点赞",
                "用户A赞了你的商品", false, 100L, "PRODUCT", FIXED_TIME);
        NotificationVO vo = new NotificationVO(1L, "LIKE", "收到点赞",
                "用户A赞了你的商品", false, 100L, "PRODUCT", FIXED_TIME);

        given(notificationService.listNotifications(1L, 1, 20)).willReturn(List.of(dto));
        given(notificationConverter.dtoListToVOList(List.of(dto))).willReturn(List.of(vo));

        mockMvc.perform(get("/notifications")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].type").value("LIKE"));
    }

    @Test
    @DisplayName("通知列表 - 按类型筛选返回信封")
    void listNotifications_WithTypeFilter_ShouldReturnEnvelope() throws Exception {
        NotificationDTO dto = new NotificationDTO(1L, 1L, "COMMENT", "收到评论",
                "用户B评论了你的商品", false, 101L, "PRODUCT", FIXED_TIME);
        NotificationVO vo = new NotificationVO(1L, "COMMENT", "收到评论",
                "用户B评论了你的商品", false, 101L, "PRODUCT", FIXED_TIME);

        given(notificationService.listNotificationsByType(1L, "COMMENT", 1, 20)).willReturn(List.of(dto));
        given(notificationConverter.dtoListToVOList(List.of(dto))).willReturn(List.of(vo));

        mockMvc.perform(get("/notifications")
                        .header("X-User-Id", 1)
                        .param("type", "COMMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].type").value("COMMENT"));
    }

    @Test
    @DisplayName("未读数量 - 成功返回信封")
    void getUnreadCount_ShouldReturnEnvelope() throws Exception {
        given(notificationService.getUnreadCount(1L)).willReturn(new UnreadCountDTO(5L));

        mockMvc.perform(get("/notifications/unread-count")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(5));
    }

    @Test
    @DisplayName("标记已读 - 成功返回信封")
    void markAsRead_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(put("/notifications/1/read")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markAsRead(1L, 1L);
    }

    @Test
    @DisplayName("全部已读 - 成功返回信封")
    void markAllAsRead_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(put("/notifications/read-all")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markAllAsRead(1L);
    }
}
