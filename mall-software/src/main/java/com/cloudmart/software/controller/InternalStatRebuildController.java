package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.time.UserBusinessCalendar;
import com.cloudmart.software.service.SoftwareDailyStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * SF06 日聚合重建端点：调度由现有任务设施托管（建议每日凌晨重算昨日窗口）；
 * 幂等——先删后插同事务，重复执行不累计翻倍。
 */
@RestController
@RequestMapping("/internal/stat-rebuild")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "内部-统计重建", description = "SF06 日聚合全量重算（任务设施调度）")
public class InternalStatRebuildController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of(UserBusinessCalendar.DEFAULT_ZONE_ID);

    private final SoftwareDailyStatService dailyStatService;
    private final Clock clock;

    @PostMapping("/run")
    @Operation(summary = "重算日聚合", description = "from/to 缺省=昨日单日；幂等可重复执行")
    public ApiResponse<Map<String, Object>> run(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate safeFrom = from == null
                ? LocalDate.now(clock.withZone(BUSINESS_ZONE)).minusDays(1) : from;
        LocalDate safeTo = to == null ? safeFrom : to;
        int rows = dailyStatService.rebuild(safeFrom, safeTo);
        return ApiResponse.ok(Map.of("from", safeFrom.toString(), "to", safeTo.toString(),
                "groups", rows));
    }
}
