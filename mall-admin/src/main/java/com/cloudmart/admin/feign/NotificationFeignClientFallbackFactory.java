package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.BroadcastNotificationRequest;
import com.cloudmart.admin.dto.feign.SendNotificationRequest;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通知服务 Feign 降级工厂。
 *
 * <p>降级语义区分（避免把一切失败伪装成"服务不可用"）：
 * <ul>
 *   <li>服务端 4xx（参数校验失败等客户端错误）：解析响应体中的业务 code/message 并透传，
 *       保留真实失败原因，便于调用方与前端定位；</li>
 *   <li>服务端 5xx、连接失败、超时：保持 NOTIFICATION_SERVICE_UNAVAILABLE 降级语义，
 *       并记录完整堆栈以定位真实故障。</li>
 * </ul>
 */
@Component
@Slf4j
public class NotificationFeignClientFallbackFactory implements FallbackFactory<NotificationFeignClient> {

    /** 降级路径专用：仅解析标准响应信封的错误字段，独立实例避免依赖容器 Jackson 配置 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public NotificationFeignClient create(Throwable cause) {
        return new NotificationFeignClient() {
            @Override
            public ApiResponse<Object> sendNotification(SendNotificationRequest request) {
                throw translate(cause);
            }

            @Override
            public ApiResponse<Object> broadcastNotification(BroadcastNotificationRequest request) {
                throw translate(cause);
            }

            @Override
            public ApiResponse<Void> deleteNotification(Long notificationId) {
                throw translate(cause);
            }
        };
    }

    private BusinessException translate(Throwable cause) {
        // 取异常链最底层的根因（Feign 的异常可能被包装）
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        if (root instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500) {
            // 服务端 4xx：透传服务端返回的业务 code/message（如参数校验失败），
            // 不伪装成"服务不可用"
            String body = fe.contentUTF8();
            log.warn("通知服务拒绝请求: status={} body={}", fe.status(), body);
            String[] codeMessage = extractServiceError(body);
            return new BusinessException(codeMessage[0], codeMessage[1]);
        }

        log.error("通知服务调用失败（连接失败/服务端错误）", cause);
        return new BusinessException("NOTIFICATION_SERVICE_UNAVAILABLE", "通知服务不可用，请稍后重试");
    }

    /** 从标准响应信封中提取 error.code / error.message，解析失败时回退为通用文案 */
    private String[] extractServiceError(String body) {
        String code = "NOTIFICATION_REQUEST_REJECTED";
        String message = "通知请求被拒绝";
        if (body == null || body.isBlank()) {
            return new String[]{code, message};
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                String errCode = error.path("code").asText(null);
                String errMsg = error.path("message").asText(null);
                if (errCode != null && !errCode.isBlank()) code = errCode;
                if (errMsg != null && !errMsg.isBlank()) message = errMsg;
                return new String[]{code, message};
            }
        } catch (Exception ignored) {
            // 非 JSON 响应体，使用通用文案
        }
        return new String[]{code, message + "（" + body.substring(0, Math.min(120, body.length())) + "）"};
    }
}
