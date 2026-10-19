package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.service.AdminSoftwareStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端运营统计（方案 7.5/10.7，P1）：仅 mall-admin 代理可达；
 * 权限裁决（software:stats）在代理层。
 */
@RestController
@RequestMapping("/admin/software")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理端-统计", description = "发布/审核时长/获取链接/收藏/评分/待办（P1）")
public class AdminSoftwareStatisticsController {

    private final AdminSoftwareStatisticsService adminSoftwareStatisticsService;
    private final com.cloudmart.software.service.SoftwareDailyStatService dailyStatService;
    private final com.cloudmart.software.repository.SoftwareMapper softwareMapper;
    private final com.cloudmart.software.repository.SoftwareSourceMapper sourceMapper;
    private final com.cloudmart.software.repository.SoftwareReleaseMapper releaseMapper;
    private final com.cloudmart.software.repository.SoftwareRevisionMapper revisionMapper;

    @GetMapping("/statistics")
    @Operation(summary = "运营统计总览",
            description = "在架数/待审数/平均审核分钟/获取链接次数与人数/收藏/新评分/待办；"
                    + "口径与窗口随响应返回，不显示外部下载完成率")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(adminSoftwareStatisticsService.overview());
    }

    /** SF06：趋势查询（最长 90 天；补零日序列 + metricDefinition） */
    @GetMapping("/statistics/trends")
    @Operation(summary = "运营趋势",
            description = "日序列：获取链接次数/人数、收藏/订阅增减与净值；口径随响应返回。"
                    + "platform 过滤按该平台有来源的软件集合生效")
    public ApiResponse<Map<String, Object>> trends(
            @org.springframework.web.bind.annotation.RequestParam(value = "from",
                    required = false) String from,
            @org.springframework.web.bind.annotation.RequestParam(value = "to",
                    required = false) String to,
            @org.springframework.web.bind.annotation.RequestParam(value = "softwareId",
                    required = false) Long softwareId,
            @org.springframework.web.bind.annotation.RequestParam(value = "categoryId",
                    required = false) Long categoryId,
            @org.springframework.web.bind.annotation.RequestParam(value = "publisherId",
                    required = false) Long publisherId,
            @org.springframework.web.bind.annotation.RequestParam(value = "platform",
                    required = false) String platform,
            @org.springframework.web.bind.annotation.RequestParam(value = "withEfficiency",
                    defaultValue = "false") boolean withEfficiency) {
        List<Long> softwareIds = resolveSoftwareIds(softwareId, categoryId, publisherId,
                platform);
        Map<String, Object> result = new java.util.LinkedHashMap<>(
                dailyStatService.trends(parseDate(from), parseDate(to), softwareIds, null));
        if (withEfficiency) {
            result.put("efficiency", dailyStatService.efficiency());
        }
        return ApiResponse.ok(result);
    }

    /** 过滤条件 → 软件ID 集合（null=不过滤；空集合以 -1 占位保证结果为空序列） */
    private List<Long> resolveSoftwareIds(Long softwareId, Long categoryId, Long publisherId,
                                          String platform) {
        boolean hasFilter = softwareId != null || categoryId != null
                || publisherId != null || (platform != null && !platform.isBlank());
        if (!hasFilter) {
            return null;
        }
        // 分类在快照上（software 表无 category_id）：categoryId → 线上快照 → 软件集合
        List<Long> categorySoftware = categoryId == null ? null
                : softwareMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.software.entity.Software>()
                                .select(com.cloudmart.software.entity.Software::getId)
                                .in(com.cloudmart.software.entity.Software::getPublishedRevisionId,
                                        revisionMapper.selectList(
                                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareRevision>()
                                                        .select(com.cloudmart.software.entity.SoftwareRevision::getId)
                                                        .eq(com.cloudmart.software.entity.SoftwareRevision::getCategoryId,
                                                                categoryId))
                                                .stream()
                                                .map(com.cloudmart.software.entity.SoftwareRevision::getId)
                                                .toList()))
                        .stream().map(com.cloudmart.software.entity.Software::getId).toList();
        if (categorySoftware != null && categorySoftware.isEmpty()) {
            return List.of(-1L);
        }
        List<Long> ids = softwareMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.software.entity.Software>()
                                .select(com.cloudmart.software.entity.Software::getId)
                                .eq(softwareId != null,
                                        com.cloudmart.software.entity.Software::getId, softwareId)
                                .eq(publisherId != null,
                                        com.cloudmart.software.entity.Software::getOwnerId,
                                        publisherId))
                .stream().map(com.cloudmart.software.entity.Software::getId).toList();
        if (categorySoftware != null) {
            ids = ids.isEmpty() ? categorySoftware
                    : ids.stream().filter(categorySoftware::contains).toList();
        }
        if (platform != null && !platform.isBlank()) {
            // platform 过滤：该平台来源 → release → 线上快照 → 软件集合
            Set<Long> revisionIds = new java.util.HashSet<>();
            for (com.cloudmart.software.entity.SoftwareSource source : sourceMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareSource>()
                            .select(com.cloudmart.software.entity.SoftwareSource::getReleaseId)
                            .eq(com.cloudmart.software.entity.SoftwareSource::getPlatform,
                                    com.cloudmart.software.enums.SoftwarePlatform.valueOf(
                                            platform.toUpperCase(java.util.Locale.ROOT))))) {
                com.cloudmart.software.entity.SoftwareRelease release =
                        releaseMapper.selectById(source.getReleaseId());
                if (release != null) {
                    revisionIds.add(release.getRevisionId());
                }
            }
            List<Long> platformSoftware = revisionIds.isEmpty() ? List.of(-1L)
                    : softwareMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.software.entity.Software>()
                                    .select(com.cloudmart.software.entity.Software::getId)
                                    .in(com.cloudmart.software.entity.Software::getPublishedRevisionId,
                                            revisionIds))
                            .stream().map(com.cloudmart.software.entity.Software::getId).toList();
            ids = ids.isEmpty() ? platformSoftware
                    : ids.stream().filter(platformSoftware::contains).toList();
        }
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    private java.time.LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : java.time.LocalDate.parse(value);
    }
}
