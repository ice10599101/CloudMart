package com.cloudmart.live.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.live.dto.WebrtcSignalRequest;
import com.cloudmart.live.dto.WebrtcSignalResponse;
import com.cloudmart.live.service.WebrtcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

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
    @DisplayName("发布信令 - 成功返回信封")
    void publishSignal_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/webrtc/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"type\":\"OFFER\",\"payload\":\"sdp-offer-data\",\"role\":\"HOST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webrtcService).publishSignal(Mockito.any(WebrtcSignalRequest.class));
    }

    @Test
    @DisplayName("获取信令 - 成功返回信封")
    void getSignals_ShouldReturnEnvelope() throws Exception {
        WebrtcSignalResponse response = new WebrtcSignalResponse("OFFER", "sdp-data", "HOST");
        given(webrtcService.getSignals(1L, "HOST")).willReturn(List.of(response));

        mockMvc.perform(get("/webrtc/signal/1/HOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].type").value("OFFER"))
                .andExpect(jsonPath("$.data[0].role").value("HOST"));
    }

    @Test
    @DisplayName("发布ICE候选者 - 成功返回信封")
    void publishIceCandidate_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/webrtc/ice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"type\":\"ICE_CANDIDATE\",\"payload\":\"ice-candidate-json\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webrtcService).publishIceCandidate(Mockito.any(WebrtcSignalRequest.class));
    }

    @Test
    @DisplayName("获取ICE候选者 - 成功返回信封")
    void getIceCandidates_ShouldReturnEnvelope() throws Exception {
        given(webrtcService.getIceCandidates(1L, "VIEWER")).willReturn(List.of("candidate:123"));

        mockMvc.perform(get("/webrtc/ice/1/VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("candidate:123"));
    }

    @Test
    @DisplayName("清除信令 - 成功返回信封")
    void clearSignals_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/webrtc/signal/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webrtcService).clearSignals(1L);
    }
}
