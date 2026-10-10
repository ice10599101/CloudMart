package com.cloudmart.pet.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetSeasonPassService;
import com.cloudmart.pet.vo.PetSeasonPassVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 赛季通行证（§6）：进行中赛季的任务经验累积 + 档位奖励领取。
 */
@RestController
@RequestMapping("/season/pass")
@RequiredArgsConstructor
@Tag(name = "赛季通行证", description = "§6：每日任务累积通行证经验，10 档奖励（宠物币+最终档皮肤）")
public class PetSeasonPassController {

    private final PetSeasonPassService passService;

    @GetMapping
    @Operation(summary = "我的通行证", description = "当前赛季 + 经验 + 10 档阶梯（达标/领取状态）；无进行中赛季 seasonId=null")
    public ApiResponse<PetSeasonPassVO> myPass(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(passService.myPass(userId));
    }

    @PostMapping("/claim")
    @Operation(summary = "领取档位奖励", description = "达标且未领取；宠物币入账（第 10 档含皮肤，皮肤码可配）")
    public ApiResponse<PetSeasonPassVO> claim(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam("tier") int tier) {
        return ApiResponse.ok(passService.claimTier(userId, tier));
    }
}
