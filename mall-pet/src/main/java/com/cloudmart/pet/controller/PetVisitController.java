package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetVisitService;
import com.cloudmart.pet.vo.PetVisitResultVO;
import com.cloudmart.pet.vo.PetVisitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 宠物串门接口（原文档 §1.1：宠物去邻居家做客）。
 */
@RestController
@RequestMapping
@Tag(name = "宠物串门", description = "邻居列表、让宠物去串门")
@RequiredArgsConstructor
public class PetVisitController {

    private final PetVisitService visitService;

    @GetMapping("/visit/neighbors")
    @Operation(summary = "串门邻居", description = "他人公开宠物（等级段优先，随机 8 只）+ 今日是否已去过")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetVisitVO>> neighbors(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(visitService.neighbors(userId));
    }

    @PostMapping("/visit/{petId}")
    @Operation(summary = "让宠物去串门", description = "消耗精力换心情/经验（服务端结算）；同一邻居每日一次；"
            + "不能串自己（409 PET_VISIT_SELF）；每日次数上限 429")
    @SentinelResource("PET_VISIT")
    public ApiResponse<PetVisitResultVO> visit(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId) {
        return ApiResponse.ok(visitService.visit(userId, petId));
    }
}
