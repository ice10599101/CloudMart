package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.CountResponse;
import com.cloudmart.admin.feign.ProductFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeignDiagnosticControllerTest {

    private MockMvc mockMvc;
    private DiscoveryClient discoveryClient;
    private ProductFeignClient productFeignClient;

    @BeforeEach
    void setUp() {
        discoveryClient = mock(DiscoveryClient.class);
        productFeignClient = mock(ProductFeignClient.class);
        FeignDiagnosticController controller = new FeignDiagnosticController(discoveryClient, productFeignClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /diagnostic/feign-test - Feign诊断测试")
    class FeignTestTests {

        @Test
        @DisplayName("Feign调用成功时返回SUCCESS结果")
        void feignTest_feignCallSucceeds_returnsSuccessResult() throws Exception {
            given(discoveryClient.getServices()).willReturn(List.of("mall-product", "mall-admin"));
            ServiceInstance instance = mock(ServiceInstance.class);
            given(instance.getHost()).willReturn("localhost");
            given(instance.getPort()).willReturn(8081);
            given(instance.getUri()).willReturn(URI.create("http://localhost:8081"));
            given(discoveryClient.getInstances("mall-product")).willReturn(List.of(instance));
            given(discoveryClient.getInstances("mall-admin")).willReturn(List.of());
            given(productFeignClient.getProductCount()).willReturn(ApiResponse.ok(new CountResponse(42L)));

            mockMvc.perform(get("/diagnostic/feign-test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.step1_discoveryClient_type").exists())
                    .andExpect(jsonPath("$.step2_registered_services").isArray())
                    .andExpect(jsonPath("$.step4_feign_call_result").value("SUCCESS"));
        }

        @Test
        @DisplayName("Feign调用失败时返回FAILED结果")
        void feignTest_feignCallFails_returnsFailedResult() throws Exception {
            given(discoveryClient.getServices()).willReturn(List.of());
            given(productFeignClient.getProductCount()).willThrow(new RuntimeException("Connection refused"));

            mockMvc.perform(get("/diagnostic/feign-test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.step4_feign_call_result").value("FAILED"))
                    .andExpect(jsonPath("$.step4_feign_call_error_message").value("Connection refused"));
        }

        @Test
        @DisplayName("无注册服务时返回空服务列表")
        void feignTest_noServices_returnsEmptyList() throws Exception {
            given(discoveryClient.getServices()).willReturn(List.of());
            given(productFeignClient.getProductCount()).willThrow(new RuntimeException("Service unavailable"));

            mockMvc.perform(get("/diagnostic/feign-test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.step2_registered_services").isEmpty());
        }
    }
}
