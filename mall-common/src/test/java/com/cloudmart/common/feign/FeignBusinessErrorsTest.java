package com.cloudmart.common.feign;

import com.cloudmart.common.exception.BusinessException;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fallback 业务错误透传契约：下游标准信封错误原样透出，
 * 非信封响应/网络故障走兜底码（熔断语义不变）。
 */
class FeignBusinessErrorsTest {

    private static final String FALLBACK_CODE = "CHAT_SERVICE_UNAVAILABLE";
    private static final String FALLBACK_MSG = "聊天服务不可用，请稍后重试";

    private FeignException feignError(int status, String body) {
        Request request = Request.create(HttpMethod.GET, "http://test/api", Map.of(), null, StandardCharsets.UTF_8, null);
        return (FeignException) FeignException.errorStatus("test",
                feign.Response.builder()
                        .status(status)
                        .reason("err")
                        .request(request)
                        .headers(Map.of())
                        .body(body, StandardCharsets.UTF_8)
                        .build());
    }

    @Test
    @DisplayName("下游业务 4xx 信封 → 透传业务 code/message")
    void parse_businessEnvelope_passthrough() {
        BusinessException e = FeignBusinessErrors.parse(
                feignError(409, "{\"success\":false,\"error\":{\"code\":\"WISH_STATUS_CONFLICT\",\"message\":\"状态冲突\"}}"),
                FALLBACK_CODE, FALLBACK_MSG);

        assertThat(e.getCode()).isEqualTo("WISH_STATUS_CONFLICT");
        assertThat(e.getMessage()).isEqualTo("状态冲突");
    }

    @Test
    @DisplayName("信封缺 message → 业务 code + 兜底 message")
    void parse_envelopeWithoutMessage_usesFallbackMessage() {
        BusinessException e = FeignBusinessErrors.parse(
                feignError(422, "{\"success\":false,\"error\":{\"code\":\"WISH_VALIDATION_ERROR\"}}"),
                FALLBACK_CODE, FALLBACK_MSG);

        assertThat(e.getCode()).isEqualTo("WISH_VALIDATION_ERROR");
        assertThat(e.getMessage()).isEqualTo(FALLBACK_MSG);
    }

    @Test
    @DisplayName("非信封响应体（网关 503 页）→ 兜底码")
    void parse_nonEnvelope_fallback() {
        BusinessException e = FeignBusinessErrors.parse(
                feignError(503, "<html>Service Unavailable</html>"),
                FALLBACK_CODE, FALLBACK_MSG);

        assertThat(e.getCode()).isEqualTo(FALLBACK_CODE);
    }

    @Test
    @DisplayName("非 FeignException（连接拒绝等）→ 兜底码")
    void parse_nonFeignThrowable_fallback() {
        BusinessException e = FeignBusinessErrors.parse(
                new IllegalStateException("connection refused"),
                FALLBACK_CODE, FALLBACK_MSG);

        assertThat(e.getCode()).isEqualTo(FALLBACK_CODE);
    }
}
