package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.SourceAppealCreateRequest;
import com.cloudmart.software.dto.software.SourceAppealCreatedVO;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareSourceAppeal;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.repository.SoftwareSourceAppealMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.service.SoftwareSourceAppealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SF07/SF08：作者工作台"待修复来源"聚合与来源申诉。
 * 只读视图与申诉提交；修复仍走既有来源反馈/编辑链路。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "小答软件-作者工作台", description = "待修复来源聚合与来源申诉（作者本人视角）")
public class SoftwareSourceIssuesController {

    private final SoftwareSourceStateMapper sourceStateMapper;
    private final com.cloudmart.software.repository.SoftwareMapper softwareMapper;
    private final com.cloudmart.software.repository.SoftwareRevisionMapper revisionMapper;
    private final SoftwareSourceAppealMapper appealMapper;
    private final SoftwareSourceAppealService appealService;

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
        // SF08：每个来源最新一条申诉（含已处理历史，供工作台显示状态）
        Map<String, SoftwareSourceAppeal> latestAppeal = new HashMap<>();
        for (SoftwareSourceAppeal appeal : appealMapper.selectList(
                new LambdaQueryWrapper<SoftwareSourceAppeal>()
                        .in(SoftwareSourceAppeal::getSoftwareId, softwareIds)
                        .orderByDesc(SoftwareSourceAppeal::getId))) {
            latestAppeal.putIfAbsent(appeal.getSoftwareId() + "|" + appeal.getSourceKey(), appeal);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SoftwareSourceState state : states) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("softwareId", String.valueOf(state.getSoftwareId()));
            row.put("softwareName", nameById.get(state.getSoftwareId()));
            row.put("sourceKey", state.getSourceKey());
            row.put("reason", state.getReason());
            row.put("checkedAt", state.getCheckedAt() == null ? null : state.getCheckedAt().toString());
            SoftwareSourceAppeal appeal = latestAppeal.get(
                    state.getSoftwareId() + "|" + state.getSourceKey());
            row.put("appealId", appeal == null ? null : String.valueOf(appeal.getId()));
            row.put("appealStatus", appeal == null ? null : appeal.getStatus());
            result.add(row);
        }
        return ApiResponse.ok(result);
    }

    /** SF08 补批：作者对当前处置提交申诉（同一来源同时只允许一条待处理）。 */
    @PostMapping("/{softwareId}/source-appeals")
    @Operation(summary = "提交来源申诉",
            description = "本人已发布软件、来源处置为 INVALID/DISABLED/SUSPECTED；理由 10~1000 字，"
                    + "证据为纯文本（不含提取码/密码/受限链接）")
    public ApiResponse<SourceAppealCreatedVO> submitAppeal(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("softwareId") Long softwareId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody SourceAppealCreateRequest request) {
        return ApiResponse.ok(appealService.submit(userId, softwareId, request, idempotencyKey));
    }

    /** SF08 补批：本人申诉记录（含处理结论）。 */
    @GetMapping("/mine/source-appeals")
    @Operation(summary = "我的来源申诉", description = "倒序最多 100 条，含处置快照与处理结论")
    public ApiResponse<List<Map<String, Object>>> mineAppeals(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(appealService.mine(userId));
    }
}
