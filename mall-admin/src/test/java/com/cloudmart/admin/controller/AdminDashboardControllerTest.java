package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.DashboardStatsResponse;
import com.cloudmart.admin.dto.RecentOrderResponse;
import com.cloudmart.admin.dto.SalesTrendItem;
import com.cloudmart.admin.service.AdminDashboardService;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDashboardControllerTest {

    private MockMvc mockMvc;
    private AdminDashboardService adminDashboardService;

    @BeforeEach
    void setUp() {
        adminDashboardService = mock(AdminDashboardService.class);
        AdminDashboardController controller = new AdminDashboardController(adminDashboardService);
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
    void getStats_returnsDashboardStats() throws Exception {
        DashboardStatsResponse stats = new DashboardStatsResponse(
                100L, 5L, 50L, 3L, 200L, new BigDecimal("9999.99"), 80L, 500L
        );
        given(adminDashboardService.getStats()).willReturn(stats);

        mockMvc.perform(get("/dashboard/stats").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userCount").value(100))
                .andExpect(jsonPath("$.data.todayOrderCount").value(200))
                .andExpect(jsonPath("$.data.todayRevenue").value(9999.99));
    }

    @Test
    void getStats_withoutAuth_returnsUnauthorized() throws Exception {
        AdminSecurityContext.clear();

        mockMvc.perform(get("/dashboard/stats").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void getRecentOrders_returnsOrderList() throws Exception {
        RecentOrderResponse order = new RecentOrderResponse(
                1L, "ORD001", "testuser", new BigDecimal("199.00"), "PAID", LocalDateTime.now()
        );
        given(adminDashboardService.getRecentOrders(10)).willReturn(List.of(order));

        mockMvc.perform(get("/dashboard/recent-orders").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].orderNo").value("ORD001"));
    }

    @Test
    void getRecentOrders_withCustomPageSize() throws Exception {
        given(adminDashboardService.getRecentOrders(5)).willReturn(List.of());

        mockMvc.perform(get("/dashboard/recent-orders").param("pageSize", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getSalesTrend_returnsTrendData() throws Exception {
        SalesTrendItem item = new SalesTrendItem("2025-01-01", new BigDecimal("500.00"), 10L);
        given(adminDashboardService.getSalesTrend(7)).willReturn(List.of(item));

        mockMvc.perform(get("/dashboard/sales-trend").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].date").value("2025-01-01"))
                .andExpect(jsonPath("$.data[0].sales").value(500.00))
                .andExpect(jsonPath("$.data[0].orders").value(10));
    }

    @Test
    void getSalesTrend_withCustomDays() throws Exception {
        given(adminDashboardService.getSalesTrend(30)).willReturn(List.of());

        mockMvc.perform(get("/dashboard/sales-trend").param("days", "30")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
