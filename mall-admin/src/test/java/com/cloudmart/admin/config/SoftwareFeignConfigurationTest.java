package com.cloudmart.admin.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 幂等键透传契约测试：管理后台把入站 Idempotency-Key 原样转发给 mall-software。
 * 缺了这一个环节，软件侧写操作会被 "Idempotency-Key 须为 1~128 字符" 全线拒绝，
 * 且错误被 FeignExceptionHandler 吞成 502，很难定位。
 */
class SoftwareFeignConfigurationTest {

    private final RequestInterceptor interceptor =
            new SoftwareFeignConfiguration().idempotencyKeyRelayInterceptor();

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest(String headerName, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerName != null) {
            request.addHeader(headerName, value);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("入站带幂等键 → 出站头部携带同值")
    void relayHeaderWhenPresent() {
        bindRequest(SoftwareFeignConfiguration.IDEMPOTENCY_KEY_HEADER, " admin-op-1 ");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get(SoftwareFeignConfiguration.IDEMPOTENCY_KEY_HEADER))
                .containsExactly("admin-op-1");
    }

    @Test
    @DisplayName("入站无幂等键 / 空白 → 不塞头，交给下游业务校验报错")
    void skipHeaderWhenBlank() {
        bindRequest(SoftwareFeignConfiguration.IDEMPOTENCY_KEY_HEADER, "   ");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(
                SoftwareFeignConfiguration.IDEMPOTENCY_KEY_HEADER);
    }

    @Test
    @DisplayName("非 HTTP 上下文（定时任务/异步）→ 不报错且不塞头")
    void skipHeaderOutsideHttpRequest() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(
                SoftwareFeignConfiguration.IDEMPOTENCY_KEY_HEADER);
    }
}
