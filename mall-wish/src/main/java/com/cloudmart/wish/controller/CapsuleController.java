package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.CreateCapsuleRequest;
import com.cloudmart.wish.service.CapsuleService;
import com.cloudmart.wish.vo.CapsuleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 时间胶囊 Controller（文档 2.7，Sprint 2.4）。
 *
 * <p>错误码：400 WISH_OPEN_AT_PAST / 400 WISH_VALIDATION_ERROR /
 * 404 WISH_NOT_FOUND（不存在或非本人）/ 409 WISH_CAPSULE_NOT_AVAILABLE
 * （未到期不可开启）。</p>
 */
@RestController
@RequestMapping("/capsules")
@Tag(name = "时间胶囊", description = "封存此刻的心意，到期拆信（跨时区按 UTC 判定）")
@RequiredArgsConstructor
public class CapsuleController {

    private final CapsuleService capsuleService;

    @PostMapping
    @Operation(summary = "创建胶囊", description = "status=SEALED；openAt 须为未来 UTC 时间"
            + "（最远 10 年）；openAtTz 记录创建时 IANA 时区仅供回溯展示")
    @SentinelResource("WISH_CAPSULE_CREATE")
    public ApiResponse<CapsuleVO> createCapsule(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CreateCapsuleRequest request) {
        return ApiResponse.ok(capsuleService.createCapsule(userId, request));
    }

    @GetMapping
    @Operation(summary = "我的胶囊列表", description = "id 倒序游标分页；"
            + "未开启/已开启通过 status 过滤分开展示；非 OPENED 项不含内容")
    @SentinelResource("WISH_CAPSULE_LIST")
    public ApiResponse<java.util.List<CapsuleVO>> listMyCapsules(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "状态过滤：SEALED/AVAILABLE/OPENED/CANCELLED（空=全部）")
            @RequestParam(required = false) String status,
            @Parameter(description = "分页游标（首页不传）") @RequestParam(required = false) String cursor,
            @Parameter(description = "页大小（默认 20，上限 50）") @RequestParam(required = false) Integer pageSize) {
        CapsuleService.CapsulePage page = capsuleService.listMyCapsules(userId, status, cursor, pageSize);
        return ApiResponse.okWithCursor(page.records(), pageSize == null ? 20 : pageSize,
                page.nextCursor(), page.hasMore());
    }

    @GetMapping("/{id}")
    @Operation(summary = "胶囊详情", description = "仅本人可见；非 OPENED 状态内容返回 null"
            + "（未到期不可见，防绕过）")
    @SentinelResource("WISH_CAPSULE_DETAIL")
    public ApiResponse<CapsuleVO> getCapsuleDetail(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(capsuleService.getCapsuleDetail(userId, id));
    }

    @PostMapping("/{id}/open")
    @Operation(summary = "到期开启", description = "SEALED/AVAILABLE 且已到期 → OPENED；"
            + "并发双开仅一次生效；重复调用幂等返回内容；未到期 409")
    @SentinelResource("WISH_CAPSULE_OPEN")
    public ApiResponse<CapsuleVO> openCapsule(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(capsuleService.openCapsule(userId, id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "取消胶囊", description = "SEALED/AVAILABLE → CANCELLED；"
            + "已开启不可取消（409 WISH_STATUS_CONFLICT）；取消后内容永久不可开启")
    @SentinelResource("WISH_CAPSULE_CANCEL")
    public ApiResponse<CapsuleVO> cancelCapsule(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(capsuleService.cancelCapsule(userId, id));
    }
}
