package com.cloudmart.file.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.file.service.AccountErasureService;
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
 * 账号数据擦除内部端点（T06）：mall-user 注销编排调用，服务令牌
 * hasRole('INTERNAL')（iss=mall-user，scope=file:internal）。幂等：重复调用无害。
 */
@RestController
@RequestMapping("/internal/account-erasure")
@Tag(name = "文件·账号擦除内部端点", description = "mall-user 注销编排专用（T06）")
@RequiredArgsConstructor
public class InternalAccountErasureController {

    private final AccountErasureService accountErasureService;

    @PostMapping
    @Operation(summary = "处置用户文件资产", description = "未引用删除/被引用匿名化；幂等")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Boolean> erase(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId) {
        return ApiResponse.ok(accountErasureService.eraseUserData(userId));
    }
}
