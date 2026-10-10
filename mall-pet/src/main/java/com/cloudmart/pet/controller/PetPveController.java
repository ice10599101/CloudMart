package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetPveService;
import com.cloudmart.pet.vo.PetPveRunVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 协作 PVE 副本（§6）：2 人协战 Boss。
 */
@RestController
@RequestMapping("/pve")
@RequiredArgsConstructor
@Tag(name = "协作 PVE", description = "§6：发起副本→队友加入→轮流攻击 Boss→一次性胜利奖励")
public class PetPveController {

    private final PetPveService pveService;

    public record StartRequest(String bossCode) {}

    @PostMapping
    @Operation(summary = "发起副本", description = "选 Boss 发起（等待队友加入）；一人同时仅一个进行中副本")
    public ApiResponse<PetPveRunVO> start(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody StartRequest request) {
        return ApiResponse.ok(pveService.start(userId, request.bossCode()));
    }

    @GetMapping("/open")
    @Operation(summary = "可加入副本列表", description = "OPEN 状态（他人发起，24h 内），供招募大厅展示")
    public ApiResponse<Page<PetPveRunVO>> open(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(pveService.openRuns(page, Math.min(size, 50)));
    }

    @PostMapping("/{runId}/join")
    @Operation(summary = "加入副本", description = "队友加入（OPEN→FIGHTING，CAS 并发兜底）；不可加入自己发起的")
    public ApiResponse<PetPveRunVO> join(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable Long runId) {
        return ApiResponse.ok(pveService.join(userId, runId));
    }

    @PostMapping("/{runId}/attack")
    @Operation(summary = "攻击", description = "轮流行动（发起人/队友）；服务端推进一攻击波次并结算 Boss 反击；CAS 防并发")
    public ApiResponse<PetPveRunVO> attack(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable Long runId) {
        return ApiResponse.ok(pveService.attack(userId, runId));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的副本", description = "发起或参与，倒序 20 条")
    public ApiResponse<Page<PetPveRunVO>> mine(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(pveService.mine(userId, page, Math.min(size, 50)));
    }

    @GetMapping("/{runId}")
    @Operation(summary = "副本详情", description = "参与者可见；OPEN 状态对外可看（招募）")
    public ApiResponse<PetPveRunVO> detail(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable Long runId) {
        return ApiResponse.ok(pveService.detail(userId, runId));
    }
}
