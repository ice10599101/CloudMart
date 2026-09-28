package com.cloudmart.wms.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wms.dto.CreateShippingRequest;
import com.cloudmart.wms.service.ShippingService;
import com.cloudmart.wms.vo.ShippingOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shipping")
@Tag(name = "物流管理", description = "物流订单的创建和查询")
public class ShippingController {

    private final ShippingService shippingService;

    public ShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('INTERNAL')") // SEC-04：履约写入口仅限服务调用方（订单履约流/管理代理）
    @Operation(summary = "创建物流订单")
    public ApiResponse<ShippingOrderVO> createShippingOrder(@Valid @RequestBody CreateShippingRequest request) {
        return ApiResponse.ok(shippingService.createShipping(request));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "根据订单ID查询物流信息", description = "用户调用校验订单归属；管理员/服务调用方跳过")
    public ApiResponse<ShippingOrderVO> getByOrderId(@PathVariable Long orderId) {
        return ApiResponse.ok(shippingService.getByOrderId(orderId, resolveCallerUserIdOrNull()));
    }

    @PutMapping("/{id}/status")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('INTERNAL')") // SEC-04：物流状态写入口仅限服务调用方
    @Operation(summary = "更新物流状态")
    public ApiResponse<ShippingOrderVO> updateStatus(
            @PathVariable Long id,
            @Parameter(description = "物流状态") @RequestParam String status) {
        return ApiResponse.ok(shippingService.updateStatus(id, status));
    }

    /**
     * SEC-04：解析调用方——服务令牌（ROLE_INTERNAL）与管理员（ROLE_ADMIN）返回 null
     * 跳过归属校验；用户调用返回令牌主体，服务层据此核验订单归属。
     */
    private Long resolveCallerUserIdOrNull() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new com.cloudmart.common.exception.BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        boolean privileged = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_INTERNAL".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
        if (privileged) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(authentication.getPrincipal()));
        } catch (NumberFormatException e) {
            throw new com.cloudmart.common.exception.BusinessException("UNAUTHORIZED", "无法识别的调用方身份");
        }
    }
}
