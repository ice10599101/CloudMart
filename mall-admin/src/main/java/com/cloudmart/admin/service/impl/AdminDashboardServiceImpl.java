package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.dto.*;
import com.cloudmart.admin.dto.feign.CountResponse;
import com.cloudmart.admin.dto.feign.OrderDTO;
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
import com.cloudmart.admin.service.AdminDashboardService;
import com.cloudmart.admin.service.AdminOnlineUserService;
import com.cloudmart.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardServiceImpl.class);

    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminMenuMapper adminMenuMapper;
    private final AdminOnlineUserService adminOnlineUserService;
    private final OrderFeignClient orderFeignClient;
    private final ProductFeignClient productFeignClient;
    private final MemberUserFeignClient memberUserFeignClient;

    public AdminDashboardServiceImpl(AdminUserMapper adminUserMapper,
                                     AdminRoleMapper adminRoleMapper,
                                     AdminMenuMapper adminMenuMapper,
                                     AdminOnlineUserService adminOnlineUserService,
                                     OrderFeignClient orderFeignClient,
                                     ProductFeignClient productFeignClient,
                                     MemberUserFeignClient memberUserFeignClient) {
        this.adminUserMapper = adminUserMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminMenuMapper = adminMenuMapper;
        this.adminOnlineUserService = adminOnlineUserService;
        this.orderFeignClient = orderFeignClient;
        this.productFeignClient = productFeignClient;
        this.memberUserFeignClient = memberUserFeignClient;
    }

    @Override
    public DashboardStatsResponse getStats() {
        long userCount = adminUserMapper.selectCount(
                new LambdaQueryWrapper<AdminUser>().isNull(AdminUser::getDeletedAt));
        long roleCount = adminRoleMapper.selectCount(
                new LambdaQueryWrapper<AdminRole>().isNull(AdminRole::getDeletedAt));
        long menuCount = adminMenuMapper.selectCount(
                new LambdaQueryWrapper<AdminMenu>().isNull(AdminMenu::getDeletedAt));
        long onlineCount = adminOnlineUserService.list().size();

        long todayOrderCount = 0;
        BigDecimal todayRevenue = BigDecimal.ZERO;
        try {
            ApiResponse<OrderTodayStatsResponse> orderStats = orderFeignClient.getTodayStats();
            if (orderStats != null && orderStats.success() && orderStats.data() != null) {
                todayOrderCount = orderStats.data().todayOrderCount();
                todayRevenue = orderStats.data().todayRevenue();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch order today stats, fallback to 0: {}", e.getMessage());
        }

        long productCount = 0;
        try {
            ApiResponse<CountResponse> productResult = productFeignClient.getProductCount();
            if (productResult != null && productResult.success() && productResult.data() != null) {
                productCount = productResult.data().count();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch product count, fallback to 0: {}", e.getMessage());
        }

        long memberCount = 0;
        try {
            ApiResponse<CountResponse> memberResult = memberUserFeignClient.getMemberCount();
            if (memberResult != null && memberResult.success() && memberResult.data() != null) {
                memberCount = memberResult.data().count();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch member count, fallback to 0: {}", e.getMessage());
        }

        return new DashboardStatsResponse(userCount, roleCount, menuCount, onlineCount,
                todayOrderCount, todayRevenue, productCount, memberCount);
    }

    @Override
    public List<RecentOrderResponse> getRecentOrders(int pageSize) {
        try {
            ApiResponse<Object> result = orderFeignClient.listOrders(null, null, null, 1, pageSize);
            if (result != null && result.success() && result.data() != null) {
                List<?> records = extractRecords(result.data());
                return records.stream()
                        .map(item -> {
                            if (!(item instanceof java.util.Map<?, ?> map)) return null;
                            return new RecentOrderResponse(
                                    map.get("id") instanceof Number n ? n.longValue() : null,
                                    (String) map.get("orderNo"),
                                    (String) map.get("receiverName"),
                                    map.get("totalAmount") instanceof java.math.BigDecimal bd ? bd : null,
                                    (String) map.get("status"),
                                    map.get("createdAt") instanceof java.time.LocalDateTime ldt ? ldt : null
                            );
                        })
                        .filter(Objects::nonNull)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch recent orders: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public List<SalesTrendItem> getSalesTrend(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        Map<LocalDate, SalesTrendAccumulator> dailyMap = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            dailyMap.put(date, new SalesTrendAccumulator());
        }

        try {
            int page = 1;
            int size = 100;
            LocalDateTime cutoff = startDate.atStartOfDay();
            boolean hasMore = true;

            while (hasMore) {
                ApiResponse<Object> result = orderFeignClient.listOrders(null, null, null, page, size);
                if (result == null || !result.success() || result.data() == null) {
                    break;
                }
                List<?> rawList = extractRecords(result.data());
                if (rawList.isEmpty()) {
                    break;
                }
                List<java.util.Map<String, Object>> orders = rawList.stream()
                        .filter(item -> item instanceof java.util.Map)
                        .map(item -> uncheckedCast(item))
                        .toList();
                for (java.util.Map<String, Object> order : orders) {
                    Object createdAtObj = order.get("createdAt");
                    if (createdAtObj instanceof LocalDateTime orderCreatedAt && orderCreatedAt.isAfter(cutoff)) {
                        LocalDate orderDate = orderCreatedAt.toLocalDate();
                        SalesTrendAccumulator acc = dailyMap.get(orderDate);
                        if (acc != null) {
                            acc.orders++;
                            Object totalAmountObj = order.get("totalAmount");
                            if (totalAmountObj instanceof java.math.BigDecimal bd) {
                                acc.sales = acc.sales.add(bd);
                            }
                        }
                    }
                }
                hasMore = orders.size() >= size;
                page++;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch orders for sales trend: {}", e.getMessage());
        }

        return dailyMap.entrySet().stream()
                .map(entry -> new SalesTrendItem(
                        entry.getKey().toString(),
                        entry.getValue().sales,
                        entry.getValue().orders
                ))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<?> extractRecords(Object data) {
        if (data instanceof List<?> list) {
            return list;
        }
        if (data instanceof java.util.Map<?, ?> map) {
            Object records = map.get("records");
            if (records instanceof List<?> list) {
                return list;
            }
        }
        return Collections.emptyList();
    }

    private static class SalesTrendAccumulator {
        BigDecimal sales = BigDecimal.ZERO;
        long orders = 0;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> uncheckedCast(Object item) {
        return (java.util.Map<String, Object>) item;
    }
}
