package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.DriftBottleInteractRequest;
import com.cloudmart.wish.dto.ThrowBottleRequest;
import com.cloudmart.wish.service.DriftBottleService;
import com.cloudmart.wish.vo.DriftBottleVO;
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

import java.util.List;

/**
 * 漂流瓶 Controller（替代附近模式相遇信笺的用户侧体验）。
 *
 * <p>路由：POST /drift-bottles（投瓶）、POST /drift-bottles/fish（捞瓶）、
 * GET /drift-bottles/mine（我的漂流瓶）、POST /drift-bottles/{id}/interactions（匿名回应）。
 * 全部需登录。</p>
 */
@RestController
@RequestMapping("/drift-bottles")
@Tag(name = "漂流瓶", description = "匿名随机漂流：投瓶 / 捞瓶 / 我的漂流瓶 / 匿名回应")
@RequiredArgsConstructor
public class DriftBottleController {

    private final DriftBottleService driftBottleService;

    @PostMapping
    @Operation(summary = "投瓶", description = "自由匿名文字或关联一个公开进行中心愿（content 与 wishId 二选一）；"
            + "投出后进入全局海面池，不暴露投瓶人身份")
    @SentinelResource("WISH_DRIFT_THROW")
    public ApiResponse<DriftBottleVO> throwBottle(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody ThrowBottleRequest request) {
        return ApiResponse.ok(driftBottleService.throwBottle(userId, request));
    }

    @PostMapping("/fish")
    @Operation(summary = "捞瓶", description = "随机捞取一个非自己的漂浮漂流瓶；海里无瓶时 data 返回 null")
    @SentinelResource("WISH_DRIFT_FISH")
    public ApiResponse<DriftBottleVO> fishBottle(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(driftBottleService.fishBottle(userId));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的漂流瓶", description = "我投出的（THROWN）+ 我捞到的（PICKED），按时间倒序")
    @SentinelResource("WISH_DRIFT_MINE")
    public ApiResponse<List<DriftBottleVO>> listMine(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(driftBottleService.listMine(userId));
    }

    @PostMapping("/{id}/interactions")
    @Operation(summary = "匿名回应", description = "仅捞起人可回应关联心愿的漂流瓶：BLESS 匿名祝福（免费）/ "
            + "LIGHT 点亮对方心愿（扣星光 2）；单瓶每日 1 次（429）；投瓶人收到匿名通知")
    @SentinelResource("WISH_DRIFT_INTERACT")
    public ApiResponse<DriftBottleVO> interact(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "漂流瓶 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody DriftBottleInteractRequest request) {
        return ApiResponse.ok(driftBottleService.interact(userId, id, request.type()));
    }
}