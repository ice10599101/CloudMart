package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.GrantConsentRequest;
import com.cloudmart.wish.enums.ConsentType;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.vo.ConsentRecordVO;
import com.cloudmart.wish.vo.ConsentStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户同意记录 Controller（文档 1.2 节 ⑳ / 34.2 合规留痕）。
 *
 * <p>首次使用 AI 功能前，客户端应引导用户同意 AI 数据处理协议
 * （consentType=AI_DATA_PROCESSING），否则树洞等 AI 接口返回 403 WISH_CONSENT_REQUIRED。</p>
 */
@RestController
@RequestMapping("/my/consents")
@Tag(name = "同意记录", description = "AI 数据处理等协议的同意/撤回留痕")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @PostMapping
    @Operation(summary = "提交同意/撤回", description = "记录协议同意（GRANT）或撤回（WITHDRAW）；"
            + "相同 (类型, 版本, 动作) 重复提交幂等返回已有记录")
    @SentinelResource("WISH_CONSENT_RECORD")
    public ApiResponse<ConsentRecordVO> recordConsent(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "同意/撤回请求") @Valid @RequestBody GrantConsentRequest request,
            HttpServletRequest httpRequest) {
        ConsentRecordVO vo = consentService.recordConsent(userId, request,
                clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ApiResponse.ok(vo);
    }

    @GetMapping
    @Operation(summary = "查询同意状态", description = "按类型查询当前同意状态（最新一条记录判定）")
    @SentinelResource("WISH_CONSENT_STATUS")
    public ApiResponse<ConsentStatusVO> getConsentStatus(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "同意类型（默认 AI_DATA_PROCESSING）")
            @RequestParam(required = false) ConsentType consentType) {
        ConsentStatusVO vo = consentService.getConsentStatus(userId,
                consentType == null ? ConsentType.AI_DATA_PROCESSING : consentType);
        return ApiResponse.ok(vo);
    }

    /**
     * 提取客户端真实 IP（网关/代理场景取 X-Forwarded-For 首个）。
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIndex = forwarded.indexOf(',');
            return commaIndex > 0 ? forwarded.substring(0, commaIndex).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
