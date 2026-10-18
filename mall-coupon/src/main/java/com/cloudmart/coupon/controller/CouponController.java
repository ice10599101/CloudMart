package com.cloudmart.coupon.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.coupon.converter.CouponTemplateConverter;
import com.cloudmart.coupon.dto.CouponTemplateDTO;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.vo.CouponTemplateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 领券中心（用户侧，只读）。
 *
 * <p>T03：原公开的模板创建（POST）与禁用（PUT /{id}/disable）已删除——它们只要求登录即可执行，
 * 属于公开写入口。模板增改启停统一走 {@code AdminCouponController}（/admin/coupon-templates，
 * mall-admin 服务令牌 + 管理权限码）。</p>
 *
 * <p>本控制器只返回用户可领/可见模板（ENABLED、未过期、有库存），不接受 status 参数读取
 * 草稿或已禁用配置；全状态查询属于管理接口职责。</p>
 */
@RestController
@RequestMapping("/coupon-templates")
@Tag(name = "领券中心", description = "用户可见优惠券模板查询（只读）")
public class CouponController {

    private final CouponService couponService;
    private final CouponTemplateConverter couponTemplateConverter;

    public CouponController(CouponService couponService, CouponTemplateConverter couponTemplateConverter) {
        this.couponService = couponService;
        this.couponTemplateConverter = couponTemplateConverter;
    }

    @GetMapping
    @Operation(summary = "查询可领优惠券模板", description = "分页查询当前用户可领取的优惠券模板")
    public ApiResponse<List<CouponTemplateVO>> listTemplates(
            @Parameter(description = "优惠券类型") @RequestParam(value = "type", required = false) String type,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        List<CouponTemplateDTO> dtos = couponService.listClaimableTemplates(type, page, pageSize);
        long total = couponService.countClaimableTemplates(type);
        return ApiResponse.ok(couponTemplateConverter.dtoListToVOList(dtos), new Meta(page, pageSize, total));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询可领优惠券模板详情", description = "根据ID查询可领取的优惠券模板，不可领按不存在处理")
    public ApiResponse<CouponTemplateVO> getTemplateById(
            @Parameter(description = "模板ID") @PathVariable("id") Long id) {
        CouponTemplateDTO dto = couponService.getClaimableTemplateById(id);
        return ApiResponse.ok(couponTemplateConverter.dtoToVO(dto));
    }
}
