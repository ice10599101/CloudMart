package com.cloudmart.payment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.payment.converter.PaymentConverter;
import com.cloudmart.payment.dto.PaymentDTO;
import com.cloudmart.payment.service.PaymentService;
import com.cloudmart.payment.vo.PaymentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/payments")
@Tag(name = "支付管理(后台)", description = "管理后台支付管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final PaymentConverter paymentConverter;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "支付列表", description = "管理后台分页查询支付记录，支持按状态筛选")
    public ApiResponse<List<PaymentVO>> listPayments(
            @Parameter(description = "支付状态") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Page<PaymentDTO> result = paymentService.listPayments(status, page, pageSize);
        List<PaymentVO> voList = result.getRecords().stream().map(paymentConverter::dtoToVO).toList();
        return ApiResponse.ok(voList, new Meta(page, pageSize, result.getTotal()));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询支付记录", description = "管理后台根据订单ID查询支付记录")
    public ApiResponse<PaymentVO> getPaymentByOrderId(
            @Parameter(description = "订单ID") @PathVariable("orderId") Long orderId) {
        PaymentDTO dto = paymentService.getPaymentByOrderId(orderId);
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }

    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "退款", description = "管理后台对已支付订单发起退款")
    public ApiResponse<PaymentVO> refund(
            @Parameter(description = "支付记录ID") @PathVariable("paymentId") Long paymentId) {
        PaymentDTO dto = paymentService.refund(paymentId);
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }
}
