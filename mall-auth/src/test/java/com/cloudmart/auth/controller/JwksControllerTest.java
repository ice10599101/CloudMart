package com.cloudmart.auth.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwksControllerTest {

    private MockMvc mockMvc;

    private final JWKSource<SecurityContext> jwkSource = Mockito.mock(JWKSource.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new JwksController(jwkSource))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /oauth2/jwks")
    class JwksTests {

        @Test
        @DisplayName("获取JWKS成功返回密钥集合")
        void jwks_ShouldReturnJwkSet() throws Exception {
            com.nimbusds.jose.jwk.JWK jwk = Mockito.mock(com.nimbusds.jose.jwk.JWK.class);
            given(jwkSource.get(any(JWKSelector.class), any())).willReturn(List.of(jwk));

            mockMvc.perform(get("/oauth2/jwks"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("JWK源异常返回错误信封")
        void jwks_WhenJwkSourceFails_ShouldReturnErrorEnvelope() throws Exception {
            given(jwkSource.get(any(JWKSelector.class), any()))
                    .willThrow(new RuntimeException("JWK source error"));

            mockMvc.perform(get("/oauth2/jwks"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("JWK_LOAD_FAILED"));
        }
    }
}
