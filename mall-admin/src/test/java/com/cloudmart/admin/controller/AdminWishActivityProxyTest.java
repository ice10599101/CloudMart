package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.WishActivityParticipantVO;
import com.cloudmart.admin.dto.WishActivityReviewRequest;
import com.cloudmart.admin.feign.WishFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADMIN-05：活动报名列表/审核代理契约——分页参数透传、审核载荷原样转发、
 * 缺省 approved 不静默按驳回处理（服务端操作者取认证上下文，信任边界不在 body）。
 */
@DisplayName("AdminWishController 活动报名代理")
class AdminWishActivityProxyTest {

    private MockMvc mockMvc;
    private WishFeignClient wishFeignClient;

    @BeforeEach
    void setUp() {
        wishFeignClient = mock(WishFeignClient.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminWishController(wishFeignClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("列表：page/size 透传，返回参与者与真实 userId")
    void listParticipants_forwardsPaging() throws Exception {
        given(wishFeignClient.listActivityParticipants(9L, 2, 10))
                .willReturn(ApiResponse.ok(List.of(new WishActivityParticipantVO(
                        42L, "PENDING", "MEMBER", 7L, "Java", 88, null, null))));

        mockMvc.perform(get("/wish/activity/9/participants?page=2&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userId").value(42))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        verify(wishFeignClient).listActivityParticipants(9L, 2, 10);
    }

    @Test
    @DisplayName("审核：approved=true 原样转发（不信任 body 中的审核人）")
    void review_forwardsApproved() throws Exception {
        given(wishFeignClient.reviewActivityParticipant(eq(9L), eq(42L), any()))
                .willReturn(ApiResponse.ok(null));

        mockMvc.perform(put("/wish/activity/9/participants/42/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<WishActivityReviewRequest> captor =
                ArgumentCaptor.forClass(WishActivityReviewRequest.class);
        verify(wishFeignClient).reviewActivityParticipant(eq(9L), eq(42L), captor.capture());
        assertThat(captor.getValue().approved()).isTrue();
    }

    @Test
    @DisplayName("审核：缺省 approved → 400，不静默按驳回处理")
    void review_missingApproved_rejected() throws Exception {
        mockMvc.perform(put("/wish/activity/9/participants/42/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
