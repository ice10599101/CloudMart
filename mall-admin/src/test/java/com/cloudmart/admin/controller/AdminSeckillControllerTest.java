package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.AddSeckillProductRequest;
import com.cloudmart.admin.dto.feign.CreateActivityRequest;
import com.cloudmart.admin.dto.feign.SeckillActivityDTO;
import com.cloudmart.admin.dto.feign.SeckillProductDTO;
import com.cloudmart.admin.feign.SeckillActivityFeignClient;
import com.cloudmart.admin.feign.SeckillProductFeignClient;
import com.cloudmart.admin.feign.SeckillRequestFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSeckillControllerTest {

    private MockMvc mockMvc;
    private SeckillActivityFeignClient seckillActivityFeignClient;
    private SeckillProductFeignClient seckillProductFeignClient;

    @BeforeEach
    void setUp() {
        seckillActivityFeignClient = mock(SeckillActivityFeignClient.class);
        seckillProductFeignClient = mock(SeckillProductFeignClient.class);

        AdminSeckillController controller = new AdminSeckillController(
                seckillActivityFeignClient,
                mock(SeckillRequestFeignClient.class),
                seckillProductFeignClient
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listSeckillActivities_returnsActivityList() throws Exception {
        given(seckillActivityFeignClient.listActivities(null))
                .willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/seckill/activities").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getSeckillActivity_returnsActivityDetail() throws Exception {
        SeckillActivityDTO activity = new SeckillActivityDTO(1L, "限时秒杀", "描述",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "ACTIVE", LocalDateTime.now());
        given(seckillActivityFeignClient.getActivity(1L))
                .willReturn(ApiResponse.ok(activity));

        mockMvc.perform(get("/business/seckill/activities/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createSeckillActivity_activityCreatedSuccessfully() throws Exception {
        SeckillActivityDTO activity = new SeckillActivityDTO(1L, "限时秒杀", "描述",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "PENDING", LocalDateTime.now());
        given(seckillActivityFeignClient.createActivity(any(CreateActivityRequest.class)))
                .willReturn(ApiResponse.ok(activity));

        mockMvc.perform(post("/business/seckill/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"限时秒杀\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateSeckillActivityStatus_statusUpdatedSuccessfully() throws Exception {
        SeckillActivityDTO activity = new SeckillActivityDTO(1L, "限时秒杀", "描述",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "ACTIVE", LocalDateTime.now());
        given(seckillActivityFeignClient.updateActivityStatus(1L, "ACTIVE"))
                .willReturn(ApiResponse.ok(activity));

        mockMvc.perform(put("/business/seckill/activities/1/status")
                        .param("status", "ACTIVE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listSeckillProducts_returnsProductList() throws Exception {
        given(seckillProductFeignClient.listProductsByActivity(1L))
                .willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/seckill/products/activity/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void addSeckillProduct_productAddedSuccessfully() throws Exception {
        SeckillProductDTO product = new SeckillProductDTO(1L, 1L, 1L,
                new BigDecimal("99.00"), new BigDecimal("199.00"), 100, 80, 1,
                "ACTIVE", LocalDateTime.now());
        given(seckillProductFeignClient.addProduct(anyLong(), any(AddSeckillProductRequest.class)))
                .willReturn(ApiResponse.ok(product));

        mockMvc.perform(post("/business/seckill/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
