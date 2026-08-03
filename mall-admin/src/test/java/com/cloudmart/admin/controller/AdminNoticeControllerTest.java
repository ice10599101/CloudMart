package com.cloudmart.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminNoticeRequest;
import com.cloudmart.admin.dto.AdminNoticeResponse;
import com.cloudmart.admin.service.AdminNoticeService;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminNoticeControllerTest {

    private MockMvc mockMvc;
    private AdminNoticeService adminNoticeService;

    @BeforeEach
    void setUp() {
        adminNoticeService = mock(AdminNoticeService.class);
        AdminNoticeController controller = new AdminNoticeController(adminNoticeService);
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
    void page_returnsPagedNotices() throws Exception {
        Page<AdminNoticeResponse> page = new Page<>(1, 20, 1);
        AdminNoticeResponse notice = new AdminNoticeResponse(1L, "系统通知", 1, "通知内容", 0, null, LocalDateTime.now(), 5L, false);
        page.setRecords(List.of(notice));
        given(adminNoticeService.page(any(), any(), anyInt(), anyInt())).willReturn(page);

        mockMvc.perform(get("/notices/page").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].noticeTitle").value("系统通知"))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void getById_returnsNoticeDetail() throws Exception {
        AdminNoticeResponse notice = new AdminNoticeResponse(1L, "系统通知", 1, "通知内容", 0, null, LocalDateTime.now(), 5L, false);
        given(adminNoticeService.getById(1L)).willReturn(notice);

        mockMvc.perform(get("/notices/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void create_noticeCreatedSuccessfully() throws Exception {
        doNothing().when(adminNoticeService).create(any(AdminNoticeRequest.class));

        mockMvc.perform(post("/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noticeTitle\":\"新通知\",\"noticeContent\":\"内容\",\"noticeType\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_noticeUpdatedSuccessfully() throws Exception {
        doNothing().when(adminNoticeService).update(anyLong(), any(AdminNoticeRequest.class));

        mockMvc.perform(put("/notices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noticeTitle\":\"更新通知\",\"noticeContent\":\"新内容\",\"noticeType\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_noticeDeletedSuccessfully() throws Exception {
        doNothing().when(adminNoticeService).delete(1L);

        mockMvc.perform(delete("/notices/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void markAsRead_markedSuccessfully() throws Exception {
        doNothing().when(adminNoticeService).markAsRead(anyLong(), anyLong());

        mockMvc.perform(post("/notices/1/read").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void unreadList_returnsUnreadNotices() throws Exception {
        AdminNoticeResponse notice = new AdminNoticeResponse(2L, "未读通知", 1, "内容", 0, null, LocalDateTime.now(), 0L, false);
        given(adminNoticeService.unreadList(1L)).willReturn(List.of(notice));

        mockMvc.perform(get("/notices/unread").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(2));
    }
}
