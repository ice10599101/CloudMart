package com.cloudmart.wish.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.dto.AdminBottleHiddenRequest;
import com.cloudmart.wish.dto.AdminBottleListQuery;
import com.cloudmart.wish.service.AdminDriftBottleService;
import com.cloudmart.wish.vo.AdminDriftBottleCommentVO;
import com.cloudmart.wish.vo.AdminDriftBottleDashboardVO;
import com.cloudmart.wish.vo.AdminDriftBottleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台漂流瓶 Controller。
 *
 * <p>路由前缀 /admin/drift-bottles，仅允许内部服务调用（mall-admin 经 Feign 代理转发），
 * ROLE_INTERNAL 由网关注入的 X-Internal-Call 头经 InternalCallAuthenticationFilter 授予。
 * 管理 VO 含真实用户 ID（治理溯源），不受用户侧匿名规则脱敏。</p>
 */
@RestController
@RequestMapping("/admin/drift-bottles")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-漂流瓶", description = "漂流瓶列表/详情/下架恢复/数据看板")
@RequiredArgsConstructor
public class AdminDriftBottleController {

    private final AdminDriftBottleService adminDriftBottleService;

    @GetMapping
    @Operation(summary = "漂流瓶列表（offset 分页）", description = "物理状态/用户（投瓶人或捞瓶人）/关键词筛选；"
            + "真实身份与管理状态（下架/收藏/回流次数）供治理使用")
    public ApiResponse<List<AdminDriftBottleVO>> listBottles(@Valid AdminBottleListQuery query) {
        Page<AdminDriftBottleVO> page = adminDriftBottleService.listBottles(query);
        return ApiResponse.ok(page.getRecords(), new ApiResponse.Meta(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal()));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "数据看板", description = "状态分布/今日投瓶/今日打捞/今日评论/近 14 天趋势/投瓶榜 Top10；"
            + "repliedCount 为有评论的瓶子数（含被扔回海里但留下评论的瓶子）")
    public ApiResponse<AdminDriftBottleDashboardVO> dashboard() {
        return ApiResponse.ok(adminDriftBottleService.dashboard());
    }

    @GetMapping("/{id}")
    @Operation(summary = "漂流瓶详情", description = "含瓶下评论全量与评论者真实用户 ID（审核溯源）")
    public ApiResponse<AdminDriftBottleService.AdminDriftBottleDetail> detail(
            @Parameter(description = "漂流瓶 ID", required = true) @PathVariable Long id) {
        return ApiResponse.ok(adminDriftBottleService.detail(id));
    }

    @PutMapping("/{id}/hidden")
    @Operation(summary = "下架/恢复漂流瓶", description = "软隐藏：下架后用户端不可见（打捞/我的漂流瓶均排除），数据保留")
    public ApiResponse<AdminDriftBottleVO> updateHidden(
            @Parameter(description = "漂流瓶 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody AdminBottleHiddenRequest request) {
        return ApiResponse.ok(adminDriftBottleService.updateHidden(id, request.isHidden()));
    }
}
