package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.service.SoftwareErasureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 软件域账号注销端点（方案 9.6/11.3）：仅 mall-user 注销编排可达
 * （iss=mall-user、scope=software:erasure，路径强校验；网关同步拦截外部直达）。
 * 幂等可续跑：全部完成才 success=true；未完成返回可重试失败，
 * 编排侧不得推进 FILE/ERASE。
 */
@RestController
@RequestMapping("/internal/account-erasure")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "内部-软件域注销", description = "mall-user 注销编排 SOFTWARE/ERASE 步骤专用")
public class InternalAccountErasureController {

    private final SoftwareErasureService softwareErasureService;

    @PostMapping
    @Operation(summary = "擦除指定用户软件域数据",
            description = "下架软件、软删评论、清理关系/授权/资格/举报身份；幂等，DONE 后重复调用返回成功")
    public ApiResponse<Boolean> erase(@RequestParam("userId") Long userId) {
        return ApiResponse.ok(softwareErasureService.eraseUser(userId));
    }
}
