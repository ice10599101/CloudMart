package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.RequestRelationRequest;
import com.cloudmart.pet.service.PetRelationService;
import com.cloudmart.pet.vo.PetRelationPanelVO;
import com.cloudmart.pet.vo.PetRelationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物关系接口（三期）：情侣（1v1）/ 闺蜜 / 兄弟 / 死党。
 */
@RestController
@RequestMapping
@Tag(name = "宠物关系", description = "关系面板/申请/确认/拒绝/解除")
@RequiredArgsConstructor
public class PetRelationController {

    private final PetRelationService relationService;

    @GetMapping("/relations")
    @Operation(summary = "关系面板", description = "已建立 + 收到申请 + 我发起的 + 候选宠物 + 类型上限")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetRelationPanelVO> relations(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(relationService.panel(userId));
    }

    @PostMapping("/relations/request")
    @Operation(summary = "申请关系", description = "情侣已有一段 409 PET_RELATION_EXCLUSIVE；重复申请 409 PET_RELATION_EXISTS")
    @SentinelResource("PET_RELATION")
    public ApiResponse<PetRelationVO> request(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody RequestRelationRequest request) {
        return ApiResponse.ok(relationService.request(userId, request));
    }

    @PostMapping("/relations/{id}/accept")
    @Operation(summary = "确认关系", description = "只有接收方主人可确认；重复处理 409 PET_RELATION_NOT_PENDING")
    @SentinelResource("PET_RELATION")
    public ApiResponse<PetRelationVO> accept(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id) {
        return ApiResponse.ok(relationService.accept(userId, id));
    }

    @PostMapping("/relations/{id}/reject")
    @Operation(summary = "拒绝关系申请")
    @SentinelResource("PET_RELATION")
    public ApiResponse<PetRelationVO> reject(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id) {
        return ApiResponse.ok(relationService.reject(userId, id));
    }

    @PostMapping("/relations/{id}/dissolve")
    @Operation(summary = "解除关系", description = "任一方可解除（保留数据置 DISSOLVED）")
    @SentinelResource("PET_RELATION")
    public ApiResponse<PetRelationVO> dissolve(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id) {
        return ApiResponse.ok(relationService.dissolve(userId, id));
    }
}
