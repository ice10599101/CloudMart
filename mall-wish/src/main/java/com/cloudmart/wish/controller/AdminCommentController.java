package com.cloudmart.wish.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.dto.AdminCommentListQuery;
import com.cloudmart.wish.dto.AdminCommentStatusRequest;
import com.cloudmart.wish.service.AdminCommentService;
import com.cloudmart.wish.vo.AdminCommentVO;
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
 * 管理后台评论 Controller（Sprint 1.2）。
 *
 * <p>路由前缀 /admin/comments，仅允许内部服务调用（mall-admin 经 Feign 代理转发），
 * ROLE_INTERNAL 由 InternalCallAuthenticationFilter 授予，外部请求无法伪造。</p>
 *
 * <p>错误码：404 WISH_NOT_FOUND（评论不存在或已删除）/ 409 WISH_STATUS_CONFLICT（状态未变化）。</p>
 */
@RestController
@RequestMapping("/admin/comments")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-评论审核", description = "评论列表查询与上下架操作")
@RequiredArgsConstructor
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    @GetMapping
    @Operation(summary = "评论列表（offset 分页）",
            description = "含已删除评论供审计；敏感词审核场景：sensitiveHit=true + status=VISIBLE 筛选待处理命中")
    public ApiResponse<List<AdminCommentVO>> listComments(@Valid AdminCommentListQuery query) {
        Page<AdminCommentVO> page = adminCommentService.listComments(query);
        return ApiResponse.ok(page.getRecords(), new ApiResponse.Meta(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal()
        ));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "评论上下架",
            description = "HIDDEN=下架（四端立即不展示），VISIBLE=恢复上架；已是目标状态返回 409")
    public ApiResponse<AdminCommentVO> updateCommentStatus(
            @Parameter(description = "评论 ID", required = true) @PathVariable("id") Long commentId,
            @Parameter(description = "目标状态请求") @Valid @RequestBody AdminCommentStatusRequest request) {
        AdminCommentVO vo = adminCommentService.updateCommentStatus(commentId, request);
        return ApiResponse.ok(vo);
    }
}
