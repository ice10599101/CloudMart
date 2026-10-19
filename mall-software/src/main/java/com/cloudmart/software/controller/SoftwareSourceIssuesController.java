package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SF07：作者工作台"待修复来源"聚合——本人软件中巡检判定 INVALID 的来源。
 * 只读视图；修复走既有来源反馈/编辑链路（此处提供待办入口不重复实现）。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "小答软件-作者工作台", description = "SF07 待修复来源聚合（作者本人视角）")
public class SoftwareSourceIssuesController {

    private final SoftwareSourceStateMapper sourceStateMapper;
    private final com.cloudmart.software.repository.SoftwareMapper softwareMapper;
    private final com.cloudmart.software.repository.SoftwareRevisionMapper revisionMapper;

    @GetMapping("/mine/source-issues")
    @Operation(summary = "待修复来源聚合",
            description = "本人软件中巡检 INVALID 的来源（来源名/原因/检查时间）；修复入口跳软件编辑页来源页签")
    public ApiResponse<List<Map<String, Object>>> mineSourceIssues(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        // 1. 本人名下软件（ACTIVE+UNLISTED；封禁/删除不进待办）
        List<Software> mine = softwareMapper.selectList(
                new LambdaQueryWrapper<Software>()
                        .eq(Software::getOwnerId, userId)
                        .in(Software::getLifecycle, "ACTIVE", "UNLISTED"));
        if (mine.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        List<Long> softwareIds = mine.stream().map(Software::getId).toList();
        var nameById = new LinkedHashMap<Long, String>();
        for (Software software : mine) {
            var revision = software.getPublishedRevisionId() == null ? null
                    : revisionMapper.selectById(software.getPublishedRevisionId());
            nameById.put(software.getId(), revision == null ? null : revision.getName());
        }
        // 2. INVALID 来源状态
        List<SoftwareSourceState> states = sourceStateMapper.selectList(
                new LambdaQueryWrapper<SoftwareSourceState>()
                        .in(SoftwareSourceState::getSoftwareId, softwareIds)
                        .eq(SoftwareSourceState::getAvailability, SourceAvailability.INVALID)
                        .orderByDesc(SoftwareSourceState::getCheckedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SoftwareSourceState state : states) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("softwareId", String.valueOf(state.getSoftwareId()));
            row.put("softwareName", nameById.get(state.getSoftwareId()));
            row.put("sourceKey", state.getSourceKey());
            row.put("reason", state.getReason());
            row.put("checkedAt", state.getCheckedAt() == null ? null : state.getCheckedAt().toString());
            result.add(row);
        }
        return ApiResponse.ok(result);
    }
}
