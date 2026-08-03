package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.service.RankingService;
import com.cloudmart.community.vo.RankingItemVO;
import com.cloudmart.community.vo.RankingSeasonVO;
import com.cloudmart.community.vo.UserRankingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/growth/ranking")
@Tag(name = "排行榜", description = "经验值排行榜，含当月实时榜单与历史赛季")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping
    @Operation(summary = "当月排行榜", description = "获取当月经验值排行榜 Top N")
    public ApiResponse<List<RankingItemVO>> getMonthlyRanking(
            @Parameter(description = "榜单大小") @RequestParam(defaultValue = "20") int size) {
        List<RankingItemVO> ranking = rankingService.getMonthlyRanking(size);
        return ApiResponse.ok(ranking);
    }

    @GetMapping("/me")
    @Operation(summary = "我的排名", description = "获取当前用户当月排名信息")
    public ApiResponse<UserRankingVO> getMyRanking(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        UserRankingVO vo = rankingService.getUserRanking(userId);
        return ApiResponse.ok(vo);
    }

    @GetMapping("/seasons")
    @Operation(summary = "历史赛季列表", description = "分页查询已归档的历史赛季")
    public ApiResponse<List<RankingSeasonVO>> getSeasons(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<RankingSeasonVO> result = rankingService.getSeasons(page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @GetMapping("/seasons/{seasonId}")
    @Operation(summary = "赛季榜单详情", description = "分页查询指定赛季的榜单记录")
    public ApiResponse<List<RankingItemVO>> getSeasonRanking(
            @Parameter(description = "赛季ID") @PathVariable Long seasonId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<RankingItemVO> result = rankingService.getSeasonRanking(seasonId, page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }
}
