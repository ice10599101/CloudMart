package com.cloudmart.community.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.TagSubscriptionService;
import com.cloudmart.community.vo.TagVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TagSubscriptionControllerTest {

    private MockMvc mockMvc;

    private final TagSubscriptionService tagSubscriptionService = Mockito.mock(TagSubscriptionService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TagSubscriptionController(tagSubscriptionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /tags/subscriptions/{tagId} - 订阅话题成功")
    void subscribe_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(tagSubscriptionService).subscribe(1L, 10L);

        mockMvc.perform(post("/tags/subscriptions/10")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /tags/subscriptions/{tagId} - 缺少X-User-Id头返回401")
    void subscribe_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/tags/subscriptions/10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("DELETE /tags/subscriptions/{tagId} - 取消订阅成功")
    void unsubscribe_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(tagSubscriptionService).unsubscribe(1L, 10L);

        mockMvc.perform(delete("/tags/subscriptions/10")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /tags/subscriptions/{tagId} - 缺少X-User-Id头返回401")
    void unsubscribe_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(delete("/tags/subscriptions/10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /tags/subscriptions/{tagId}/status - 已订阅返回true")
    void checkSubscription_Subscribed_ShouldReturnTrue() throws Exception {
        given(tagSubscriptionService.isSubscribed(1L, 10L)).willReturn(true);

        mockMvc.perform(get("/tags/subscriptions/10/status")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("GET /tags/subscriptions/{tagId}/status - 未订阅返回false")
    void checkSubscription_NotSubscribed_ShouldReturnFalse() throws Exception {
        given(tagSubscriptionService.isSubscribed(1L, 10L)).willReturn(false);

        mockMvc.perform(get("/tags/subscriptions/10/status")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("GET /tags/subscriptions - 获取已订阅话题列表成功")
    void getSubscribedTags_ShouldReturnSuccess() throws Exception {
        TagVO tagVO = new TagVO(1L, "技术", null, 50, true, 1, LocalDateTime.now());
        given(tagSubscriptionService.getSubscribedTags(1L)).willReturn(List.of(tagVO));

        mockMvc.perform(get("/tags/subscriptions")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("技术"));
    }

    @Test
    @DisplayName("GET /tags/subscriptions - 缺少X-User-Id头返回401")
    void getSubscribedTags_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/tags/subscriptions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
