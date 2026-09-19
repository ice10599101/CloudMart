package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.CreatePetRequest;
import com.cloudmart.pet.dto.RenamePetRequest;
import com.cloudmart.pet.dto.UpdateAppearanceRequest;
import com.cloudmart.pet.dto.UpdatePrivacyRequest;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetPublicVO;
import com.cloudmart.pet.vo.PetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物基础接口：领养/查询/改名/外观/隐私 + 他人主页公开卡片。
 */
@RestController
@RequestMapping
@Tag(name = "宠物基础", description = "领养、我的宠物、改名、外观、公开资料")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    @GetMapping("/me")
    @Operation(summary = "我的宠物", description = "懒更新结算后的权威状态 + 进行中/可领取活动 + 今日喂食余量；"
            + "顺带触发主动消息评估（每日问候/社区播报等，频控每日≤3条）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetVO> myPet(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(petService.getMyPet(userId));
    }

    @PostMapping("/create")
    @Operation(summary = "领养宠物", description = "一期一用户一宠（uk_pet_user 兜底 409 PET_ALREADY_EXISTS）；"
            + "种类/性格/外观走白名单校验")
    @SentinelResource("PET_CREATE")
    public ApiResponse<PetVO> create(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @org.springframework.web.bind.annotation.RequestBody CreatePetRequest request) {
        return ApiResponse.ok(petService.createPet(userId, request));
    }

    @PutMapping("/name")
    @Operation(summary = "宠物改名", description = "30 天一次（409 PET_RENAME_COOLDOWN）；1-12 字符")
    public ApiResponse<PetVO> rename(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @org.springframework.web.bind.annotation.RequestBody RenamePetRequest request) {
        return ApiResponse.ok(petService.renamePet(userId, request));
    }

    @PutMapping("/appearance")
    @Operation(summary = "修改外观", description = "颜色/配饰白名单；一期免费")
    public ApiResponse<PetVO> updateAppearance(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateAppearanceRequest request) {
        return ApiResponse.ok(petService.updateAppearance(userId, request));
    }

    @PutMapping("/privacy")
    @Operation(summary = "主页公开开关", description = "关闭后他人主页不展示宠物卡片（404 PET_NOT_PUBLIC）")
    public ApiResponse<Void> updatePrivacy(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdatePrivacyRequest request) {
        petService.updatePrivacy(userId, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/public/{userId}")
    @Operation(summary = "他人主页宠物卡片", description = "公开资料：名称/种类/等级/成长阶段/成就数；未公开或无宠物 404")
    public ApiResponse<PetPublicVO> publicPet(@PathVariable("userId") Long userId) {
        return ApiResponse.ok(petService.getPublicPet(userId));
    }
}
