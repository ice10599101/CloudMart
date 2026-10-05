package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.community.service.AccountErasureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号数据擦除内部端点（T06 补齐）：mall-user 注销编排调用，
 * 服务令牌 hasRole('INTERNAL')（iss=mall-user）。幂等：重复调用无害。
 */
@RestController
@RequestMapping("/internal/account-erasure")
@Tag(name = "社区·账号擦除内部端点", description = "mall-user 注销编排专用（T06）")
@RequiredArgsConstructor
public class InternalAccountErasureController {

    private final AccountErasureService accountErasureService;

    @PostMapping
    @Operation(summary = "擦除用户社区数据", description = "帖子/评论软删+去标识化；幂等")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Boolean> erase(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId) {
        return ApiResponse.ok(accountErasureService.eraseUserData(userId));
    }
}
