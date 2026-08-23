package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.ReportTimezoneRequest;
import com.cloudmart.wish.service.CapsuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户资料 Controller（文档 2.15，合规 26 时区策略）。
 *
 * <p>客户端登录/启动/时区变化时上报 IANA 时区（仅变化时调用，客户端缓存
 * 上次上报值）；服务端用于胶囊创建时 openAtTimezone 回填与本地时区定时
 * 任务（文档 9.2 wish-ai-reminder）。</p>
 */
@RestController
@RequestMapping("/my")
@Tag(name = "用户资料", description = "时区上报（跨时区到期判定合规策略）")
@RequiredArgsConstructor
public class MyProfileController {

    private final CapsuleService capsuleService;

    @PostMapping("/timezone")
    @Operation(summary = "上报时区", description = "写入 wish_user_stat.timezone，重复上报幂等；"
            + "offsetMinutes 仅校验不存储（IANA 时区已含 DST 信息）")
    @SentinelResource("WISH_TIMEZONE_REPORT")
    public ApiResponse<Map<String, Object>> reportTimezone(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody ReportTimezoneRequest request) {
        return ApiResponse.ok(capsuleService.reportTimezone(userId, request.timezone(), request.offsetMinutes()));
    }
}
