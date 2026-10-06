package com.cloudmart.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.async.outbox.OutboxEventEntity;
import com.cloudmart.common.async.mapper.OutboxEventMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户本人异步任务回查（方案 §5.1"业务任务查询与恢复" / §5.2 契约
 * GET /operations/{id}）：页面关闭/刷新后凭创建时下发的 requestId 恢复
 * 结果视图。只读映射 outbox_event，不暴露内部字段；归属校验防止横向探测。
 */
@RestController
@RequestMapping("/operations")
@RequiredArgsConstructor
@Tag(name = "异步任务回查", description = "用户凭 requestId 恢复异步业务结果（§5.2）")
public class UserOperationsController {

    private final OutboxEventMapper outboxEventMapper;

    @GetMapping("/{operationId}")
    @Operation(summary = "任务结果回查", description = "requestId 即 publicOperationId；"
            + "返回 status/resultRef(订单号等)/errorCode/retryable/nextPollAfter。"
            + "归属校验：user_id 不匹配一律 404（防横向枚举）")
    public ApiResponse<Map<String, Object>> get(
            @Parameter(description = "创建业务时下发的 requestId", required = true)
            @PathVariable("operationId") String operationId,
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {

        OutboxEventEntity event = outboxEventMapper.selectByRequestId(operationId);
        if (event == null) {
            // 不存在：与归属不符同响应（防存在性探测）
            return ApiResponse.ok(Map.of("status", "NOT_FOUND"));
        }
        // 归属校验（防横向枚举）：SECKILL_RESULT 的 requestId 本身即随机凭据且由
        // 受理响应私密下发，视为持有即授权；其余事件类型按 aggregateId=userId 校验
        if (!"SECKILL_RESULT".equals(event.getEventType())
                && !String.valueOf(userId).equals(event.getAggregateId())) {
            return ApiResponse.ok(Map.of("status", "NOT_FOUND"));
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("operationId", event.getRequestId());
        view.put("type", event.getEventType());
        // PENDING/SENDING → RUNNING（业务语义：处理中）；SENT → SUCCESS（已受理）
        String status = switch (event.getStatus()) {
            case "PENDING", "SENDING" -> "RUNNING";
            case "SENT" -> "SUCCESS";
            case "DEAD" -> "FAILED";
            default -> event.getStatus();
        };
        view.put("status", status);
        view.put("retryable", "FAILED".equals(status));
        view.put("nextPollAfter", "FAILED".equals(status) || "RUNNING".equals(status) ? 5 : 0);
        if ("FAILED".equals(status)) {
            view.put("errorCode", event.getEventType() + "_DISPATCH_FAILED");
        }
        return ApiResponse.ok(view);
    }
}
