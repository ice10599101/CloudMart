package com.cloudmart.seckill.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.dto.SeckillQuoteDTO;
import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.service.SeckillRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部报价快照接口（T09）：mall-order 建秒杀订单前经服务令牌回查请求事实，
 * 以冻结快照的 seckillPrice 计价并校验主体/SKU/状态——秒杀价与普通价不混用，
 * 消息载荷价格不作为定价依据。
 */
@RestController
@RequestMapping("/internal/seckill/requests")
@Tag(name = "内部-秒杀请求", description = "mall-order 建单回查报价快照（服务令牌可达）")
public class InternalSeckillRequestController {

    private final SeckillRequestService requestService;

    public InternalSeckillRequestController(SeckillRequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询报价快照", description = "按 requestId 返回冻结快照；不存在或非排队状态抛业务异常")
    public ApiResponse<SeckillQuoteDTO> quote(
            @Parameter(description = "请求ID", required = true) @PathVariable("requestId") String requestId) {
        SeckillRequest request = requestService.findByRequestId(requestId);
        if (request == null) {
            throw new BusinessException("SECKILL_QUOTE_NOT_FOUND", "秒杀请求不存在");
        }
        return ApiResponse.ok(new SeckillQuoteDTO(request.getRequestId(), request.getUserId(),
                request.getActivityId(), request.getProductId(), request.getSkuId(),
                request.getQuantity(), request.getSeckillPrice(), request.getStatus(), request.getOrderId()));
    }
}
