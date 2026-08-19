package com.cloudmart.wish.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.dto.AdminInteractionListQuery;
import com.cloudmart.wish.service.AdminInteractionService;
import com.cloudmart.wish.vo.AdminInteractionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台互动记录 Controller（Sprint 1.2）。
 *
 * <p>路由前缀 /admin/interactions，仅允许内部服务调用（mall-admin 经 Feign 代理转发），
 * ROLE_INTERNAL 由 InternalCallAuthenticationFilter 授予，外部请求无法伪造。</p>
 */
@RestController
@RequestMapping("/admin/interactions")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-互动记录", description = "互动记录审计查询（含已取消记录）")
@RequiredArgsConstructor
public class AdminInteractionController {

    private final AdminInteractionService adminInteractionService;

    @GetMapping
    @Operation(summary = "互动记录列表（offset 分页）",
            description = "含已取消（软删）记录的完整审计轨迹；支持心愿/用户/类型/时间范围筛选")
    public ApiResponse<List<AdminInteractionVO>> listInteractions(@Valid AdminInteractionListQuery query) {
        Page<AdminInteractionVO> page = adminInteractionService.listInteractions(query);
        return ApiResponse.ok(page.getRecords(), new ApiResponse.Meta(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal()
        ));
    }
}
