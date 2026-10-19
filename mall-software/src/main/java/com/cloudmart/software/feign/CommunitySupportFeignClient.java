package com.cloudmart.software.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.dto.software.InteractionContextRequest;
import com.cloudmart.software.dto.software.SoftwareInteractionContextVO;
import com.cloudmart.software.dto.software.SoftwareLevelOptionVO;
import com.cloudmart.software.dto.software.SoftwareLevelVO;
import com.cloudmart.software.dto.software.SoftwareReviewOutcomeVO;
import com.cloudmart.software.dto.software.SoftwareReviewRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 社区只读适配客户端（方案 9.6）：出站令牌由 ServiceTokenFeignInterceptor
 * 按 outbound-scopes 自动签名（iss=mall-software、aud=mall-community、
 * scope=community:software-support）。
 */
@FeignClient(name = "mall-community", contextId = "softwareCommunitySupportFeignClient",
        fallbackFactory = CommunitySupportFeignClientFallbackFactory.class)
public interface CommunitySupportFeignClient {

    @GetMapping("/internal/software-support/level")
    ApiResponse<SoftwareLevelVO> getLevel(@RequestParam("userId") Long userId);

    @GetMapping("/internal/software-support/level-configs")
    ApiResponse<List<SoftwareLevelOptionVO>> getLevelConfigs();

    @PostMapping("/internal/software-support/review")
    ApiResponse<SoftwareReviewOutcomeVO> review(@RequestBody SoftwareReviewRequest request);

    @PostMapping("/internal/software-support/interaction-context")
    ApiResponse<SoftwareInteractionContextVO> interactionContext(
            @RequestBody InteractionContextRequest request);

    /** UX04：批量查询帖子公开状态（软件教程入口；谓词与社区可读一致） */
    @GetMapping("/internal/software-support/posts/status")
    ApiResponse<java.util.List<java.util.Map<String, Object>>> postPublicStatus(
            @RequestParam("ids") java.util.List<Long> ids);
}
