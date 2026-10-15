package com.cloudmart.common.handler;

import com.cloudmart.common.api.ApiResponse;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 异常处理器契约：下游按标准信封返回的业务错误必须原样透传，不能被折叠成
 * BAD_GATEWAY——否则管理端看到的只是"服务调用失败"，真实原因（幂等键非法、
 * 状态冲突等）被吞掉，排障只能靠日志。
 */
class FeignExceptionHandlerTest {

    private final FeignExceptionHandler handler = new FeignExceptionHandler();

    private FeignException feignError(int status, String responseBody) {
        Request request = Request.create(Request.HttpMethod.POST, "http://downstream/api",
                Map.of(), Request.Body.empty(), new RequestTemplate());
        byte[] body = responseBody == null
                ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8);
        return FeignException.errorStatus("SoftwareFeignClient#op()",
                feign.Response.builder()
                        .status(status)
                        .reason("reason")
                        .request(request)
                        .headers(Map.of())
                        .body(body)
                        .build());
    }

    @Test
    @DisplayName("下游 400 标准信封 → 透传业务码与 HTTP 状态")
    void relayBusinessError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleFeignException(
                feignError(400, "{\"success\":false,\"error\":{\"code\":\"SOFTWARE_OPERATION_INVALID\","
                        + "\"message\":\"Idempotency-Key 须为 1~128 字符\"}}"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("SOFTWARE_OPERATION_INVALID");
        assertThat(response.getBody().error().message()).isEqualTo("Idempotency-Key 须为 1~128 字符");
    }

    @Test
    @DisplayName("下游 409 冲突 → 透传冲突码，不被降级为 502")
    void relayConflictStatus() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleFeignException(
                feignError(409, "{\"success\":false,\"error\":{\"code\":\"SOFTWARE_OPERATION_CONFLICT\","
                        + "\"message\":\"该 Idempotency-Key 已被不同请求使用\"}}"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("SOFTWARE_OPERATION_CONFLICT");
    }

    @Test
    @DisplayName("响应体非信封（网关 HTML 页）→ 按网络故障降级 502")
    void fallbackToBadGatewayWhenBodyIsNotEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleFeignException(
                feignError(400, "<html><body>Bad Request</body></html>"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("BAD_GATEWAY");
    }

    @Test
    @DisplayName("5xx 空响应体 → SERVICE_UNAVAILABLE（熔断语义不变）")
    void serviceUnavailableOnServerError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleFeignException(feignError(503, ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("SERVICE_UNAVAILABLE");
    }
}
