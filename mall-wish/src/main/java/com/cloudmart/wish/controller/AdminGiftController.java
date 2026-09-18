package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.dto.AdminGiftRequest;
import com.cloudmart.wish.service.GiftService;
import com.cloudmart.wish.vo.GiftRecordVO;
import com.cloudmart.wish.vo.GiftVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理后台礼物管理 Controller（全站虚拟礼物）。
 *
 * <p>路由前缀 /admin/gifts，仅内部服务调用（mall-admin 经 Feign 代理转发，
 * hasRole('INTERNAL') 由 X-Internal-Call 头授予）；权限点
 * {@code business:gift:*} 在管理后台角色界面配置。</p>
 */
@RestController
@RequestMapping("/admin/gifts")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-礼物管理", description = "礼物目录 CRUD + 上下架 + 送礼记录查询")
@RequiredArgsConstructor
public class AdminGiftController {

    private final GiftService giftService;

    @GetMapping
    @Operation(summary = "全量礼物目录", description = "含下架礼物（管理端表格展示全部），sort 升序")
    public ApiResponse<List<GiftVO>> listGifts() {
        return ApiResponse.ok(giftService.adminListGifts());
    }

    @PostMapping
    @Operation(summary = "新增礼物", description = "图标经 mall-file 上传后登记 URL；默认未上架（需再调启停）")
    public ApiResponse<GiftVO> createGift(@Valid @RequestBody AdminGiftRequest request) {
        return ApiResponse.ok(giftService.adminCreateGift(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑礼物", description = "名称/图标/动效/单价/排序/描述可改")
    public ApiResponse<GiftVO> updateGift(
            @Parameter(description = "礼物 ID", required = true) @PathVariable("id") Long giftId,
            @Valid @RequestBody AdminGiftRequest request) {
        return ApiResponse.ok(giftService.adminUpdateGift(giftId, request));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "上架/下架礼物", description = "仅上架礼物对用户端可见；下架不影响历史送礼记录")
    public ApiResponse<GiftVO> updateGiftStatus(
            @Parameter(description = "礼物 ID", required = true) @PathVariable("id") Long giftId,
            @RequestBody Map<String, Boolean> body) {
        Boolean onShelf = body.get("onShelf");
        return ApiResponse.ok(giftService.adminUpdateGiftStatus(giftId, onShelf != null && onShelf));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除礼物", description = "软删（保留审计轨迹）；历史送礼记录持有快照不受影响")
    public ApiResponse<Void> deleteGift(
            @Parameter(description = "礼物 ID", required = true) @PathVariable("id") Long giftId) {
        giftService.adminDeleteGift(giftId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/records")
    @Operation(summary = "送礼记录列表", description = "id 倒序 offset 分页（管理表格）；可按送礼人/收礼人/场景组合筛选")
    public ApiResponse<List<GiftRecordVO>> listRecords(
            @Parameter(description = "送礼人用户 ID")
            @RequestParam(value = "senderId", required = false) Long senderId,
            @Parameter(description = "收礼人用户 ID")
            @RequestParam(value = "receiverId", required = false) Long receiverId,
            @Parameter(description = "送礼场景")
            @RequestParam(value = "targetType", required = false) String targetType,
            @Parameter(description = "场景对象 ID")
            @RequestParam(value = "targetId", required = false) Long targetId,
            @Parameter(description = "页码（默认 1）")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "页大小（默认 20，上限 100）")
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(giftService.adminListRecords(senderId, receiverId, targetType, targetId, page, pageSize));
    }
}
