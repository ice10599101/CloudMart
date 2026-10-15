package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.community.dto.software.InteractionContextRequest;
import com.cloudmart.community.dto.software.SoftwareReviewRequest;
import com.cloudmart.community.service.SoftwareSupportService;
import com.cloudmart.community.vo.software.SoftwareInteractionContextVO;
import com.cloudmart.community.vo.software.SoftwareLevelOptionVO;
import com.cloudmart.community.vo.software.SoftwareLevelVO;
import com.cloudmart.community.vo.software.SoftwareReviewOutcomeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小答软件域内部支持接口（方案 9.6）：仅 mall-software 持服务令牌可达
 * （iss=mall-software、scope=community:software-support，路径强校验），
 * 不可经公开网关访问。等级/审核/拉黑均为只读适配，不提供经验发放或规则写入。
 */
@RestController
@RequestMapping("/internal/software-support")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "内部-小答软件支持", description = "mall-software 专用：等级/审核/互动资格窄接口")
public class InternalSoftwareSupportController {

    private final SoftwareSupportService softwareSupportService;

    @GetMapping("/level")
    @Operation(summary = "实时查询用户社区等级",
            description = "resolve 的 COMMUNITY_LEVEL 模式每次调用实时读取；沿用首次查询创建 1 级规则")
    public ApiResponse<SoftwareLevelVO> level(@RequestParam("userId") Long userId) {
        return ApiResponse.ok(softwareSupportService.level(userId));
    }

    @GetMapping("/level-configs")
    @Operation(summary = "启用中的等级配置",
            description = "供发布表单展示（可短缓存 60s）；提交与 resolve 仍实时校验")
    public ApiResponse<List<SoftwareLevelOptionVO>> levelConfigs() {
        return ApiResponse.ok(softwareSupportService.levelOptions());
    }

    @PostMapping("/review")
    @Operation(summary = "批量审核软件文本节点",
            description = "texts 为软件服务消毒后的纯文本节点（社区不解析 HTML）；结果同序同长并回传 contentHash")
    public ApiResponse<SoftwareReviewOutcomeVO> review(@RequestBody SoftwareReviewRequest request) {
        return ApiResponse.ok(softwareSupportService.review(request));
    }

    @PostMapping("/interaction-context")
    @Operation(summary = "双向拉黑互动资格",
            description = "评论列表按候选作者批量查询；只返回拉黑结论，账号状态由软件服务另行核对")
    public ApiResponse<SoftwareInteractionContextVO> interactionContext(
            @RequestBody InteractionContextRequest request) {
        return ApiResponse.ok(softwareSupportService.interactionContext(request));
    }
}
