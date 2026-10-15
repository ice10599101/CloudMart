package com.cloudmart.common.handler;

import com.cloudmart.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@ConditionalOnClass(name = "feign.FeignException")
public class FeignExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FeignExceptionHandler.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeignException(FeignException ex) {
        log.error("Feign call failed: status={}", ex.status(), ex);
        // 下游按标准信封返回的业务错误（400/409/404 等）必须原样透传：
        // 折叠成 BAD_GATEWAY 会吞掉 SOFTWARE_OPERATION_INVALID、
        // WISH_STATUS_CONFLICT 一类业务码，让用户看到"服务调用失败"而不是真实原因。
        // 与 com.cloudmart.common.feign.FeignBusinessErrors（fallback 场景）保持同一语义。
        ApiResponse<?> envelope = readEnvelope(ex);
        if (envelope != null && envelope.error() != null
                && envelope.error().code() != null && !envelope.error().code().isBlank()) {
            HttpStatus resolved = HttpStatus.resolve(ex.status());
            log.info("下游业务错误透传: code={}, status={}", envelope.error().code(), ex.status());
            return ResponseEntity.status(resolved == null ? HttpStatus.BAD_GATEWAY : resolved)
                    .body(ApiResponse.fail(envelope.error().code(), envelope.error().message()));
        }
        if (ex.status() >= 500) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.fail("SERVICE_UNAVAILABLE", "下游服务暂时不可用"));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail("BAD_GATEWAY", "服务调用失败"));
    }

    /** 响应体为标准 ApiResponse 信封时返回它；连接失败、网关 HTML 页等返回 null。 */
    private ApiResponse<?> readEnvelope(FeignException ex) {
        String body = ex.contentUTF8();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(body, ApiResponse.class);
        } catch (Exception parseFailure) {
            log.debug("下游响应体非标准信封，按网络故障处理: {}", parseFailure.getMessage());
            return null;
        }
    }
}
