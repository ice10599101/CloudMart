package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.dto.AdminOnlineUserResponse;
import com.cloudmart.admin.dto.DashboardStatsResponse;
import com.cloudmart.admin.dto.RecentOrderResponse;
import com.cloudmart.admin.dto.SalesTrendItem;
import com.cloudmart.admin.dto.feign.CountResponse;
import com.cloudmart.admin.dto.feign.OrderTodayStatsResponse;
import com.cloudmart.admin.entity.AdminMenu;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.feign.MemberUserFeignClient;
import com.cloudmart.admin.feign.OrderFeignClient;
import com.cloudmart.admin.feign.ProductFeignClient;
import com.cloudmart.admin.repository.AdminMenuMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminUserMapper;
import com.cloudmart.admin.service.AdminOnlineUserService;
import com.cloudmart.common.api.ApiResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminDashboardServiceImplTest {

    private AdminUserMapper adminUserMapper;
    private AdminRoleMapper adminRoleMapper;
    private AdminMenuMapper adminMenuMapper;
    private AdminOnlineUserService adminOnlineUserService;
    private OrderFeignClient orderFeignClient;
    private ProductFeignClient productFeignClient;
    private MemberUserFeignClient memberUserFeignClient;
    private AdminDashboardServiceImpl adminDashboardService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{AdminUser.class, AdminRole.class, AdminMenu.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                MybatisConfiguration configuration = new MybatisConfiguration();
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                assistant.setCurrentNamespace("com.cloudmart.admin.repository." + clazz.getSimpleName() + "Mapper");
                TableInfoHelper.initTableInfo(assistant, clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        adminUserMapper = mock(AdminUserMapper.class);
        adminRoleMapper = mock(AdminRoleMapper.class);
        adminMenuMapper = mock(AdminMenuMapper.class);
        adminOnlineUserService = mock(AdminOnlineUserService.class);
        orderFeignClient = mock(OrderFeignClient.class);
        productFeignClient = mock(ProductFeignClient.class);
        memberUserFeignClient = mock(MemberUserFeignClient.class);

        adminDashboardService = new AdminDashboardServiceImpl(
                adminUserMapper, adminRoleMapper, adminMenuMapper,
                adminOnlineUserService, orderFeignClient,
                productFeignClient, memberUserFeignClient);
    }

    @Nested
    @DisplayName("getStats")
    class GetStatsTests {

        @Test
        @DisplayName("success with feign data -> returns complete stats")
        void getStats_SuccessWithFeignData_ShouldReturnCompleteStats() {
            when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L);
            when(adminRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
            when(adminMenuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(20L);
            when(adminOnlineUserService.list()).thenReturn(List.of(
                    new AdminOnlineUserResponse(1L, "admin", "Admin", null, "127.0.0.1", LocalDateTime.now(), "t1"),
                    new AdminOnlineUserResponse(2L, "editor", "Editor", null, "192.168.1.1", LocalDateTime.now(), "t2")
            ));

            ApiResponse<OrderTodayStatsResponse> orderStats = ApiResponse.ok(
                    new OrderTodayStatsResponse(15, new BigDecimal("9999.99")));
            when(orderFeignClient.getTodayStats()).thenReturn(orderStats);

            ApiResponse<CountResponse> productResult = ApiResponse.ok(new CountResponse(100L));
            when(productFeignClient.getProductCount()).thenReturn(productResult);

            ApiResponse<CountResponse> memberResult = ApiResponse.ok(new CountResponse(500L));
            when(memberUserFeignClient.getMemberCount()).thenReturn(memberResult);

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
        @DisplayName("feign failure -> fallback to zero for feign fields")
        void getStats_FeignFailure_ShouldFallbackToZero() {
            when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L);
            when(adminRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
            when(adminMenuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(20L);
            when(adminOnlineUserService.list()).thenReturn(List.of());

            when(orderFeignClient.getTodayStats()).thenThrow(new RuntimeException("Order service unavailable"));
            when(productFeignClient.getProductCount()).thenThrow(new RuntimeException("Product service unavailable"));
            when(memberUserFeignClient.getMemberCount()).thenThrow(new RuntimeException("Member service unavailable"));

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
    }

    @Nested
    @DisplayName("getRecentOrders")
    class GetRecentOrdersTests {

        @Test
        @DisplayName("success -> returns recent order responses")
        void getRecentOrders_Success_ShouldReturnOrders() {
            Map<String, Object> orderMap = Map.of(
                    "id", 1,
                    "orderNo", "ORD-001",
                    "receiverName", "张三",
                    "totalAmount", new BigDecimal("199.50"),
                    "status", "PAID",
                    "createdAt", LocalDateTime.of(2025, 6, 1, 12, 0)
            );
            ApiResponse<Object> result = ApiResponse.ok(Map.of("records", List.of(orderMap)));
            when(orderFeignClient.listOrders(any(), any(), any(), anyInt(), anyInt())).thenReturn(result);

            List<RecentOrderResponse> orders = adminDashboardService.getRecentOrders(10);

            assertThat(orders).hasSize(1);
            RecentOrderResponse order = orders.getFirst();
            assertThat(order.id()).isEqualTo(1L);
            assertThat(order.orderNo()).isEqualTo("ORD-001");
            assertThat(order.username()).isEqualTo("张三");
            assertThat(order.totalAmount()).isEqualByComparingTo(new BigDecimal("199.50"));
            assertThat(order.status()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("feign failure -> returns empty list")
        void getRecentOrders_FeignFailure_ShouldReturnEmptyList() {
            when(orderFeignClient.listOrders(any(), any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Order service unavailable"));

            List<RecentOrderResponse> orders = adminDashboardService.getRecentOrders(10);

            assertThat(orders).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSalesTrend")
    class GetSalesTrendTests {

        @Test
        @DisplayName("success -> returns sales trend items")
        void getSalesTrend_Success_ShouldReturnTrendItems() {
            Map<String, Object> orderMap = Map.of(
                    "id", 1,
                    "orderNo", "ORD-001",
                    "totalAmount", new BigDecimal("299.00"),
                    "status", "PAID",
                    "createdAt", LocalDateTime.now()
            );
            ApiResponse<Object> result = ApiResponse.ok(Map.of("records", List.of(orderMap)));
            when(orderFeignClient.listOrders(any(), any(), any(), anyInt(), anyInt())).thenReturn(result);

            List<SalesTrendItem> trend = adminDashboardService.getSalesTrend(7);

            assertThat(trend).hasSize(7);
            assertThat(trend.getFirst().date()).isNotNull();
        }

        @Test
        @DisplayName("feign failure -> returns empty trend with zero values")
        void getSalesTrend_FeignFailure_ShouldReturnEmptyTrend() {
            when(orderFeignClient.listOrders(any(), any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Order service unavailable"));

            List<SalesTrendItem> trend = adminDashboardService.getSalesTrend(7);

            assertThat(trend).hasSize(7);
            assertThat(trend).allSatisfy(item -> {
                assertThat(item.sales()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(item.orders()).isZero();
            });
        }
    }
}
