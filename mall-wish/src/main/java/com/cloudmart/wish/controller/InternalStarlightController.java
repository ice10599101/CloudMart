package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.PetWalletOperationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部星光发放 Controller（N-2 评价返星光等跨服务激励）。
 *
 * <p>与 /internal/pet-support/starlight/earn（PET_REWARD 专用）分离：
 * 本端点接收显式 source 并按白名单校验，避免跨业务流水混用审计口径。
 * hasRole('INTERNAL') 由服务令牌建立（SEC-01），发行方白名单见
 * mall-community/mall-wish yml 的 service-token-paths（P2-24 教训：双向都要配）。</p>
 */
@RestController
@RequestMapping("/internal/starlight")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "内部-星光发放", description = "跨服务业务激励的幂等星光发放")
public class InternalStarlightController {

    private final UserStatService userStatService;

    /** 允许经本端点发放的来源白名单（新业务接入需显式登记，防流水来源滥用） */
    private static final Map<String, ResourceLogSource> ALLOWED_SOURCES = Map.of(
            "REVIEW_REWARD", ResourceLogSource.REVIEW_REWARD,
            "INVITE_REWARD", ResourceLogSource.INVITE_REWARD);

    @PostMapping("/earn")
    @Operation(summary = "幂等发放星光", description = "携带 operationId 幂等：重复相同请求返回原结果，"
            + "同键不同内容返回 WISH_OPERATION_CONFLICT(409)；余额达上限时截断（credited < amount）")
    public ApiResponse<PetWalletOperationVO> earn(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId,
            @Parameter(description = "星光数量（正整数）", required = true) @RequestParam("amount") Integer amount,
            @Parameter(description = "关联业务 ID（评价记录 ID，审计用）", required = true) @RequestParam("refId") Long refId,
            @Parameter(description = "业务操作唯一键（B01 幂等）", required = true) @RequestParam("operationId") String operationId,
            @Parameter(description = "流水来源（白名单校验）", required = true) @RequestParam("source") String source) {
        ResourceLogSource resolved = ALLOWED_SOURCES.get(source);
        if (resolved == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "WISH_SOURCE_NOT_ALLOWED", "星光来源未登记：" + source);
        }
        return ApiResponse.ok(userStatService.earnStarlightIdempotent(userId, amount, resolved, refId, operationId));
    }
}
