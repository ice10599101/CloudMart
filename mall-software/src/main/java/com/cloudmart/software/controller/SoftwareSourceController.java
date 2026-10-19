package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.service.SoftwareSourceAccessService;
import com.cloudmart.software.service.SoftwareSourceFeedbackService;
import com.cloudmart.software.vo.software.SoftwareAccessStateVO;
import com.cloudmart.software.vo.software.SoftwareFeedbackVO;
import com.cloudmart.software.vo.software.SoftwareResolveVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 来源访问端点（方案 5.2/5.3/7.3）：resolve 与 access-state 必须 no-store
 * （Cache-Control: no-store，不经公共 CDN 缓存）；真实链接/提取码仅 resolve 返回。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "小答软件-来源", description = "获取链接/失效反馈/查看条件状态")
public class SoftwareSourceController {

    private final SoftwareSourceAccessService sourceAccessService;
    private final SoftwareSourceFeedbackService sourceFeedbackService;

    @PostMapping("/{id}/sources/{sourceId}/resolve")
    @Operation(summary = "获取下载信息",
            description = "统一校验登录/软件可见/来源归属/可用性/查看条件；返回真实链接与提取码；no-store")
    public ApiResponse<SoftwareResolveVO> resolve(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @PathVariable("sourceId") Long sourceId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(sourceAccessService.resolve(userId, id, sourceId));
    }

    @PostMapping("/{id}/sources/{sourceId}/feedback")
    @Operation(summary = "失效反馈",
            description = "同指纹同人去重（重复提交返回现有反馈）；24h 3 账号标 SUSPECTED")
    public ApiResponse<SoftwareFeedbackVO> feedback(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            @PathVariable("sourceId") Long sourceId,
            @Valid @RequestBody FeedbackRequest request) {
        return ApiResponse.ok(sourceFeedbackService.feedback(userId, id, sourceId,
                request.getReasonCode(), request.getDescription(),
                request.getReleaseKey(), request.getPlatform(), request.getArch(),
                request.getSystemVersionText()));
    }

    @GetMapping("/{id}/access-state")
    @Operation(summary = "查看条件判定",
            description = "mode/allowed/reasonCode/等级或授权信息/联系入口；不返回真实来源；no-store")
    public ApiResponse<SoftwareAccessStateVO> accessState(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long id,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(sourceAccessService.accessState(userId, id));
    }

    /** 失效反馈请求体（5.3） */
    @Getter
    @Setter
    public static class FeedbackRequest {
        @NotBlank(message = "反馈类型不能为空")
        private String reasonCode;
        private String description;
        /** SF02：兼容性反馈定位字段（PLATFORM_MISMATCH/INSTALL_FAILURE 时使用） */
        private String releaseKey;
        private String platform;
        private String arch;
        private String systemVersionText;
    }
}
