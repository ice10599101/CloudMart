package com.cloudmart.common.handler;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler 业务码 → HTTP 状态映射")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpStatusCode statusOf(String code) {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(new BusinessException(code, "msg"));
        return response.getStatusCode();
    }

    @Test
    @DisplayName("跨服务降级码 {SERVICE}_SERVICE_UNAVAILABLE 统一 503（含历史遗漏的码）")
    void shouldMapAllServiceUnavailableCodesTo503() {
        assertThat(statusOf("WISH_SERVICE_UNAVAILABLE")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(statusOf("CHAT_SERVICE_UNAVAILABLE")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(statusOf("CAPSULE_SERVICE_UNAVAILABLE")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(statusOf("TREE_ENV_SERVICE_UNAVAILABLE")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(statusOf("ADMIN_SERVICE_UNAVAILABLE")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(statusOf("PRODUCT_SERVICE_UNAVAILABLE")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(statusOf("COMMUNITY_SERVICE_UNAVAILABLE")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        // 新增服务无需登记即可获得 503
        assertThat(statusOf("SOME_FUTURE_SERVICE_UNAVAILABLE")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("非降级后缀的同形码不命中通配")
    void shouldNotMisMapSimilarPrefixes() {
        // WISH_AI_UNAVAILABLE 不以 _SERVICE_UNAVAILABLE 结尾，走显式分支
        assertThat(statusOf("WISH_AI_UNAVAILABLE")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("既有映射保持稳定")
    void shouldKeepExistingMappings() {
        assertThat(statusOf("GIFT_NOT_FOUND")).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf("GIFT_TARGET_NOT_FOUND")).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf("POLL_NOT_FOUND")).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf("SURVEY_NOT_FOUND")).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf("POLL_ALREADY_VOTED")).isEqualTo(HttpStatus.CONFLICT);
        assertThat(statusOf("GIFT_OFF_SHELF")).isEqualTo(HttpStatus.CONFLICT);
        assertThat(statusOf("UPLOAD_DAILY_LIMIT_EXCEEDED")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(statusOf("WISH_STARLIGHT_INSUFFICIENT")).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(statusOf("TOTALLY_UNKNOWN_CODE")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleBusinessException(new BusinessException(null, "msg")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
