package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.LearnSkillRequest;
import com.cloudmart.pet.service.PetSkillService;
import com.cloudmart.pet.vo.PetSkillVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 宠物技能接口（原文档 §89 宠物技能：商城买书 → 学习 → 战斗/捞瓶/读书生效）。
 */
@RestController
@RequestMapping
@Tag(name = "宠物技能", description = "技能列表、技能学习")
@RequiredArgsConstructor
public class PetSkillController {

    private final PetSkillService skillService;

    @GetMapping("/skills")
    @Operation(summary = "技能列表", description = "全部上架技能 + 我的学习/背包状态 + 服务端生成的效果文案")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetSkillVO>> skills(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(skillService.skills(userId));
    }

    @PostMapping("/skills/learn")
    @Operation(summary = "学习技能", description = "需背包已有技能书（否则 409 PET_SKILL_BOOK_REQUIRED）；"
            + "重复学习 409 PET_SKILL_ALREADY_LEARNED")
    @SentinelResource("PET_SKILL")
    public ApiResponse<PetSkillVO> learn(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody LearnSkillRequest request) {
        return ApiResponse.ok(skillService.learn(userId, request));
    }
}
