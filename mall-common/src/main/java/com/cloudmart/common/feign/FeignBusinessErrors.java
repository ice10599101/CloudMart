package com.cloudmart.common.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback 业务错误透传（T16/T22 审计改进）：下游返回的业务错误（4xx，
 * 响应体为标准 {@link ApiResponse} 信封）不应被 fallback 无条件折叠为
 * "服务不可用"——那会吞掉 WISH_STATUS_CONFLICT、WISH_VALIDATION_ERROR
 * 等业务码，用户提示失真且排障困难。
 *
 * <p>解析策略：cause 为 {@link FeignException} 时尝试从响应体提取业务
 * code/message 抛出；响应体非信封（连接失败、503 网关页等）或解析失败，
 * 才降级为调用方给定的兜底码。真正的网络级故障保持熔断语义不变。</p>
 */
@Slf4j
public final class FeignBusinessErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FeignBusinessErrors() {
    }

    /**
     * 从 fallback cause 解析下游业务错误；无法识别业务信封时返回兜底错误。
     *
     * @param cause           fallback 收到的原始异常
     * @param fallbackCode    兜底错误码（服务不可用语义）
     * @param fallbackMessage 兜底提示
     */
    public static BusinessException parse(Throwable cause, String fallbackCode, String fallbackMessage) {
        if (cause instanceof FeignException feignException) {
            String body = feignException.contentUTF8();
            if (body != null && !body.isBlank()) {
                try {
                    ApiResponse<?> envelope = MAPPER.readValue(body, ApiResponse.class);
                    if (envelope.error() != null
                            && envelope.error().code() != null
                            && !envelope.error().code().isBlank()) {
                        String code = envelope.error().code();
                        String message = envelope.error().message() == null
                                || envelope.error().message().isBlank()
                                        ? fallbackMessage : envelope.error().message();
                        log.info("下游业务错误透传: code={}, status={}", code, feignException.status());
                        return new BusinessException(code, message, cause);
                    }
                } catch (Exception parseFailure) {
                    // 响应体不是标准信封（网关 503 页、HTML 等）→ 走兜底
                    log.debug("fallback 响应体非标准信封，走兜底: {}", parseFailure.getMessage());
                }
            }
        }
        return new BusinessException(fallbackCode, fallbackMessage, cause);
    }
}
