package com.cloudmart.user.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.user.dto.software.SoftwareProfilesRequest;
import com.cloudmart.user.service.SoftwareSupportService;
import com.cloudmart.user.vo.software.SoftwareProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小答软件域内部资料接口（方案 9.6）：仅 mall-software 持服务令牌可达
 * （iss=mall-software、scope=user:software-support），不可经公开网关访问。
 * 借用 admin 完整用户资料接口被明确禁止——本接口是唯一授权路径。
 */
@RestController
@RequestMapping("/internal/software-support")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "内部-小答软件支持", description = "mall-software 专用：批量最小资料")
public class InternalSoftwareSupportController {

    private final SoftwareSupportService softwareSupportService;

    @PostMapping("/profiles")
    @Operation(summary = "批量查询用户最小资料",
            description = "仅昵称/头像/账号可用性；结果仅含存在的 ID（逐 ID 缺失由调用方识别）；上限 100")
    public ApiResponse<List<SoftwareProfileVO>> profiles(
            @Valid @RequestBody SoftwareProfilesRequest request) {
        return ApiResponse.ok(softwareSupportService.profiles(request.userIds()));
    }
}
