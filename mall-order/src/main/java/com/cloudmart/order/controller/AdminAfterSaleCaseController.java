package com.cloudmart.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.dto.AfterSaleCaseVO;
import com.cloudmart.order.service.AfterSaleCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 售后案件运营端（T11）：mall-admin 经服务令牌转发（order:admin 可达）。
 * 受理必须关联/创建 T02 退款单（refundNo 必填）——案件批准与资金退款分离。
 */
@RestController
@RequestMapping("/admin/orders/after-sale")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "内部-售后案件", description = "后台售后受理/拒绝/分页（服务令牌可达）")
public class AdminAfterSaleCaseController {

    private final AfterSaleCaseService afterSaleCaseService;

    public record ApproveRequest(BigDecimal refundAmount, String refundNo) {}

    public record RejectRequest(String rejectReason) {}

    public record InspectRequest(String result, String note) {}

    @PostMapping("/{caseId}/inspection")
    @Operation(summary = "质检结果录入", description = "T11：人工质检 PASSED/REJECTED（需已登记退货运单）")
    public ApiResponse<Void> inspection(
            @Parameter(description = "案件ID", required = true) @PathVariable Long caseId,
            @RequestBody InspectRequest request) {
        afterSaleCaseService.recordInspection(operatorId(), caseId, request.result(), request.note());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{caseId}/approve")
    @Operation(summary = "受理售后", description = "PENDING→APPROVED，冻结批准金额并关联退款单")
    public ApiResponse<AfterSaleCaseVO> approve(
            @Parameter(description = "案件ID", required = true) @PathVariable Long caseId,
            @RequestBody ApproveRequest request) {
        return ApiResponse.ok(afterSaleCaseService.approve(operatorId(), caseId,
                request.refundAmount(), request.refundNo()));
    }

    @PostMapping("/{caseId}/reject")
    @Operation(summary = "拒绝售后", description = "PENDING→REJECTED，留拒绝原因")
    public ApiResponse<AfterSaleCaseVO> reject(
            @Parameter(description = "案件ID", required = true) @PathVariable Long caseId,
            @RequestBody RejectRequest request) {
        return ApiResponse.ok(afterSaleCaseService.reject(operatorId(), caseId, request.rejectReason()));
    }

    /** 操作者：admin JWT 主体（AdminSecurityContext 由 UserJwtAuthenticationFilter 建立） */
    private Long operatorId() {
        var ctx = com.cloudmart.common.context.AdminSecurityContext.get();
        return ctx == null ? null : ctx.userId();
    }

    @GetMapping
    @Operation(summary = "售后案件分页", description = "按状态/订单筛选")
    public ApiResponse<Page<AfterSaleCaseVO>> page(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "orderId", required = false) Long orderId) {
        return ApiResponse.ok(afterSaleCaseService.pageForAdmin(status, orderId, page, pageSize));
    }
}
