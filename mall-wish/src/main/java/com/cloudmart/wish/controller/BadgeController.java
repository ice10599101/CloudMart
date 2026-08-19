package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.service.BadgeService;
import com.cloudmart.wish.vo.BadgeDefinitionVO;
import com.cloudmart.wish.vo.BadgeWallItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 徽章 Controller（文档 2.6 徽章墙 / 2.9 徽章图鉴）。
 *
 * <p>徽章授予无独立接口：由统计变更点同步判定（心愿创建 / 帮助他人 MQ 消费）。</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "徽章", description = "徽章墙聚合视图与徽章图鉴")
public class BadgeController {

    private final BadgeService badgeService;

    @GetMapping("/my/badges")
    @Operation(summary = "我的徽章墙", description = "全部徽章聚合视图：已获得返回 earnedAt，"
            + "未获得返回 condition + progress（灰色锁定态展示获取方式与进度）；"
            + "已获得在前按获得时间倒序")
    public ApiResponse<List<BadgeWallItemVO>> getBadgeWall(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(badgeService.getBadgeWall(userId));
    }

    @GetMapping("/badges/definitions")
    @Operation(summary = "徽章图鉴（公开）", description = "全部徽章定义列表，含 condition 与 rarity；"
            + "无需登录（未登录用户亦可浏览图鉴）")
    public ApiResponse<List<BadgeDefinitionVO>> getDefinitions() {
        return ApiResponse.ok(badgeService.getDefinitions());
    }
}
