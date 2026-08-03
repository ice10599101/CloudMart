package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.dto.AdminOnlineUserResponse;
import com.cloudmart.admin.dto.DashboardStatsResponse;
import com.cloudmart.admin.dto.RecentOrderResponse;
import com.cloudmart.admin.dto.feign.CountResponse;
import com.cloudmart.admin.dto.feign.OrderTodayStatsResponse;
import com.cloudmart.admin.feign.MemberUserFeignClient;
import com.cloudmart.admin.feign.OrderFeignClient;
import com.cloudmart.admin.feign.ProductFeignClient;
import com.cloudmart.admin.repository.AdminMenuMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminUserMapper;
import com.cloudmart.admin.service.AdminOnlineUserService;
import com.cloudmart.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private AdminRoleMapper adminRoleMapper;
    @Mock
    private AdminMenuMapper adminMenuMapper;
    @Mock
    private AdminOnlineUserService adminOnlineUserService;
    @Mock
    private OrderFeignClient orderFeignClient;
    @Mock
    private ProductFeignClient productFeignClient;
    @Mock
    private MemberUserFeignClient memberUserFeignClient;

    private AdminDashboardServiceImpl adminDashboardService;

    @BeforeEach
    void setUp() {
        adminDashboardService = new AdminDashboardServiceImpl(
                adminUserMapper, adminRoleMapper, adminMenuMapper,
                adminOnlineUserService, orderFeignClient,
                productFeignClient, memberUserFeignClient
        );
    }

    @Test
    void getStats_success() {
        when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L);
        when(adminRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        when(adminMenuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(20L);
        when(adminOnlineUserService.list()).thenReturn(List.of(
                new AdminOnlineUserResponse(1L, "admin", "Admin", "Tech", "127.0.0.1", null, "token1"),
                new AdminOnlineUserResponse(2L, "user", "User", "Tech", "127.0.0.2", null, "token2")
        ));

        OrderTodayStatsResponse orderStats = new OrderTodayStatsResponse(15L, new BigDecimal("9999.99"));
        when(orderFeignClient.getTodayStats()).thenReturn(ApiResponse.ok(orderStats));

        CountResponse productCountResp = new CountResponse(100L);
        when(productFeignClient.getProductCount()).thenReturn(ApiResponse.ok(productCountResp));

        CountResponse memberCountResp = new CountResponse(500L);
        when(memberUserFeignClient.getMemberCount()).thenReturn(ApiResponse.ok(memberCountResp));

        DashboardStatsResponse stats = adminDashboardService.getStats();

        assertThat(stats.userCount()).isEqualTo(10L);
        assertThat(stats.roleCount()).isEqualTo(5L);
        assertThat(stats.menuCount()).isEqualTo(20L);
        assertThat(stats.onlineCount()).isEqualTo(2L);
        assertThat(stats.todayOrderCount()).isEqualTo(15L);
        assertThat(stats.todayRevenue()).isEqualByComparingTo(new BigDecimal("9999.99"));
        assertThat(stats.productCount()).isEqualTo(100L);
        assertThat(stats.memberCount()).isEqualTo(500L);
    }

    @Test
    void getStats_feignFailure_fallbackToZero() {
        when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L);
        when(adminRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        when(adminMenuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(20L);
        when(adminOnlineUserService.list()).thenReturn(List.of());

        when(orderFeignClient.getTodayStats()).thenThrow(new RuntimeException("Service unavailable"));
        when(productFeignClient.getProductCount()).thenThrow(new RuntimeException("Service unavailable"));
        when(memberUserFeignClient.getMemberCount()).thenThrow(new RuntimeException("Service unavailable"));

        DashboardStatsResponse stats = adminDashboardService.getStats();

        assertThat(stats.userCount()).isEqualTo(10L);
        assertThat(stats.roleCount()).isEqualTo(5L);
        assertThat(stats.menuCount()).isEqualTo(20L);
        assertThat(stats.onlineCount()).isZero();
        assertThat(stats.todayOrderCount()).isZero();
        assertThat(stats.todayRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.productCount()).isZero();
        assertThat(stats.memberCount()).isZero();
    }

    @Test
    void getRecentOrders_success() {
        Map<String, Object> order1 = new HashMap<>();
        order1.put("id", 1L);
        order1.put("orderNo", "ORD001");
        order1.put("receiverName", "Alice");
        order1.put("totalAmount", new BigDecimal("199.99"));
        order1.put("status", "PAID");
        order1.put("createdAt", LocalDateTime.of(2026, 5, 29, 10, 0));

        Map<String, Object> recordsMap = new HashMap<>();
        recordsMap.put("records", List.of(order1));

        when(orderFeignClient.listOrders(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(ApiResponse.ok(recordsMap));

        List<RecentOrderResponse> result = adminDashboardService.getRecentOrders(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).orderNo()).isEqualTo("ORD001");
        assertThat(result.get(0).username()).isEqualTo("Alice");
        assertThat(result.get(0).totalAmount()).isEqualByComparingTo(new BigDecimal("199.99"));
        assertThat(result.get(0).status()).isEqualTo("PAID");
    }

    @Test
    void getRecentOrders_feignFailure_returnsEmptyList() {
        when(orderFeignClient.listOrders(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Service unavailable"));

        List<RecentOrderResponse> result = adminDashboardService.getRecentOrders(10);

        assertThat(result).isEmpty();
    }
}
