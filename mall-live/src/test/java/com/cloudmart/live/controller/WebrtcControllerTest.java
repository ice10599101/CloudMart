package com.cloudmart.live.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.live.service.WebrtcService;
import com.cloudmart.live.service.WebrtcSignalTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebrtcControllerTest {

    private MockMvc mockMvc;

    private final WebrtcService webrtcService = Mockito.mock(WebrtcService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WebrtcController(webrtcService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("签发信令票据 - 返回 ticket/role/peerSessionId/expiresAt")
    void issueTicket_ShouldReturnEnvelope() throws Exception {
        given(webrtcService.issueSignalTicket(1001L, 1L)).willReturn(
                new WebrtcSignalTicketService.IssuedTicket(
                        "tk-1", "VIEWER", "ps-1", Instant.parse("2026-01-01T00:01:00Z")));

        mockMvc.perform(post("/webrtc/tickets")
                        .header("X-User-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticket").value("tk-1"))
                .andExpect(jsonPath("$.data.role").value("VIEWER"))
                .andExpect(jsonPath("$.data.peerSessionId").value("ps-1"));
    }

    @Test
    @DisplayName("发布信令 - 成功返回信封（身份以票据为准）")
    void publishSignal_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/webrtc/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"ticket\":\"tk-1\",\"type\":\"OFFER\",\"payload\":\"sdp-offer-data\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webrtcService).publishSignal(any(com.cloudmart.live.dto.WebrtcSignalRequest.class));
    }

    @Test
    @DisplayName("获取信令 - ticket 为必填查询参数")
    void getSignals_ShouldRequireTicket() throws Exception {
        given(webrtcService.getSignals(1L, "HOST", "tk-1")).willReturn(List.of());

        mockMvc.perform(get("/webrtc/signal/1/HOST")
                        .param("ticket", "tk-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webrtcService).getSignals(1L, "HOST", "tk-1");
    }

    @Test
    @DisplayName("获取信令 - 缺 ticket 参数 400")
    void getSignals_MissingTicket_BadRequest() throws Exception {
        mockMvc.perform(get("/webrtc/signal/1/HOST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("发布 ICE - 成功返回信封")
    void publishIce_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/webrtc/ice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"ticket\":\"tk-1\",\"type\":\"ICE_CANDIDATE\",\"payload\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webrtcService).publishIceCandidate(any(com.cloudmart.live.dto.WebrtcSignalRequest.class));
    }

    @Test
    @DisplayName("清除信令 - 房主票据经查询参数传递")
    void clearSignals_ShouldPassTicket() throws Exception {
        mockMvc.perform(delete("/webrtc/signal/1")
                        .param("ticket", "tk-host"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webrtcService).clearSignals(eq(1L), eq("tk-host"));
    }
}
