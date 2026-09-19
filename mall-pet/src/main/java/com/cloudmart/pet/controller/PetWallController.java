package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.PostWallMessageRequest;
import com.cloudmart.pet.dto.ReplyWallMessageRequest;
import com.cloudmart.pet.service.PetWallService;
import com.cloudmart.pet.vo.PetWallLikeVO;
import com.cloudmart.pet.vo.PetWallMessageVO;
import com.cloudmart.pet.vo.PetWallPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物留言墙接口（三期）。
 *
 * <p>留言软删（作者/墙主人）+ 管理端可隐藏；点赞幂等（再次点击取消）。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物留言墙", description = "留言墙分页/留言/主人回复/点赞/删除")
@RequiredArgsConstructor
public class PetWallController {

    private final PetWallService wallService;

    @GetMapping("/wall/{petId}")
    @Operation(summary = "留言墙", description = "一级留言（含主人回复）分页；家园未公开 403 PET_ROOM_PRIVATE")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetWallPageVO> wall(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(wallService.list(userId, petId, page, size));
    }

    @PostMapping("/wall/messages")
    @Operation(summary = "留言", description = "1-120 字；每日上限 429 PET_WALL_RATE_LIMITED；未公开 403")
    @SentinelResource("PET_WALL")
    public ApiResponse<PetWallMessageVO> post(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody PostWallMessageRequest request) {
        return ApiResponse.ok(wallService.post(userId, request));
    }

    @PostMapping("/wall/messages/reply")
    @Operation(summary = "主人回复", description = "只有墙主人可回复自己墙上的一级留言")
    @SentinelResource("PET_WALL")
    public ApiResponse<PetWallMessageVO> reply(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody ReplyWallMessageRequest request) {
        return ApiResponse.ok(wallService.reply(userId, request));
    }

    @DeleteMapping("/wall/messages/{id}")
    @Operation(summary = "删除留言", description = "作者或墙主人可删（软删保留审核轨迹）")
    @SentinelResource("PET_WALL")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id) {
        wallService.delete(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/wall/messages/{id}/like")
    @Operation(summary = "点赞/取消点赞", description = "uk 幂等：首次点赞计数 +1，再次点击取消")
    @SentinelResource("PET_WALL")
    public ApiResponse<PetWallLikeVO> like(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id) {
        return ApiResponse.ok(wallService.like(userId, id));
    }
}
