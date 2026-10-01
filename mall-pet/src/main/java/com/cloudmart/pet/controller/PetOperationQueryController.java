package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetRequestDedup;
import com.cloudmart.pet.repository.PetRequestDedupMapper;
import com.cloudmart.pet.util.PetJsonUtils;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * P02：按原请求键查询操作结果（方案 12.4 GET /pet/operations/{requestKey}）。
 *
 * <p>429/503/网络丢包后客户端用原 requestKey 查询即可拿回原结果或确认处理中，
 * 不得换键重发。FAILED 表示执行者丢失且无已提交业务事实——同键重发是安全的。</p>
 */
@RestController
@RequestMapping("/operations")
@RequiredArgsConstructor
@Tag(name = "宠物操作查询", description = "按请求键查询幂等操作状态（仅本人）")
public class PetOperationQueryController {

    private final PetRequestDedupMapper dedupMapper;

    @GetMapping("/{requestKey}")
    @Operation(summary = "按请求键查询操作状态",
            description = "PROCESSING=处理中（请等待后重查）；COMPLETED=已完成（result 为原终态响应）；"
                    + "FAILED=无已提交业务事实（同键重发安全）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<OperationStatusVO> operation(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "客户端请求键（发起时的原幂等键）", required = true)
            @PathVariable String requestKey,
            @Parameter(description = "逻辑操作码（默认 PURCHASE）")
            @RequestParam(defaultValue = "PURCHASE") String endpointKey) {
        PetRequestDedup row = dedupMapper.selectOne(new LambdaQueryWrapper<PetRequestDedup>()
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, endpointKey)
                .eq(PetRequestDedup::getRequestKey, requestKey));
        if (row == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "未找到该请求键的操作记录");
        }
        Object result = null;
        if ("COMPLETED".equals(row.getStatus()) && row.getResponseJson() != null) {
            result = PetJsonUtils.parse(row.getResponseJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        }
        return ApiResponse.ok(new OperationStatusVO(row.getEndpointKey(), row.getRequestKey(),
                row.getStatus(), result));
    }

    /** 操作状态（P02：result 仅在 COMPLETED 时返回原终态响应快照） */
    public record OperationStatusVO(String endpointKey, String requestKey, String status, Object result) {
    }
}
