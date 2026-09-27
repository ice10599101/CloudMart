package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.AccountDeletionService;
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
 * 账号数据擦除内部端点（B20 编排）：mall-user 注销编排调用，
 * 服务令牌 scope=wish:erasure（iss=mall-user）。幂等：重复调用无害。
 */
@RestController
@RequestMapping("/internal/account-erasure")
@Tag(name = "心愿宇宙·账号擦除内部端点", description = "mall-user 注销编排专用（B20）")
@RequiredArgsConstructor
public class InternalAccountErasureController {

    private final AccountDeletionService accountDeletionService;

    @PostMapping
    @Operation(summary = "擦除用户心愿数据", description = "软删该用户全部心愿（保留审计）；幂等")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Boolean> erase(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId) {
        return ApiResponse.ok(accountDeletionService.eraseUserData(userId));
    }
}
