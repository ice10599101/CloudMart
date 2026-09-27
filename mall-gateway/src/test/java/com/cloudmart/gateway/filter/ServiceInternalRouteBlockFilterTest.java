package com.cloudmart.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B01/SEC-01 网关阻断测试（T01/T02 对应网关层断言）：
 * 普通/编码/大小写/重复斜杠/尾部斜杠/点段的 wish、pet admin/internal 路径全部 404；
 * 用户公开路径不受影响。
 */
@DisplayName("ServiceInternalRouteBlockFilter 外部直达阻断")
class ServiceInternalRouteBlockFilterTest {

    private final ServiceInternalRouteBlockFilter filter = new ServiceInternalRouteBlockFilter();

    private MockServerWebExchange exchange(String uri) {
        // 用 URI 直构，避免 MockServerHttpRequest 把 %2F 等转义当模板二次编码
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(org.springframework.http.HttpMethod.GET,
                        java.net.URI.create(uri)).build());
        GatewayFilterChain chain = mockChain();
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private GatewayFilterChain mockChain() {
        return currentExchange -> Mono.empty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://gw/api/wish/admin/wishes",
            "http://gw/api/wish/admin",
            "http://gw/api/wish/admin/",
            "http://gw/api/wish/admin/wishes/123/delete",
            "http://gw/api/wish/ADMIN/wishes",
            "http://gw/api/wish/Admin/comments",
            "http://gw/api/wish//admin//wishes",
            "http://gw/api/wish/internal/jobs/overdue-scan",
            "http://gw/api/wish/internal",
            "http://gw/api/wish/internal/pet-support/starlight/earn",
            "http://gw/api/wish/INTERNAL/pet-support/starlight/spend",
            "http://gw/api/wish/admin%2Fwishes",
            "http://gw/api/wish/%61dmin/wishes",
            "http://gw/api/wish/admin;x=1/wishes",
            "http://gw/api/wish/./admin/wishes",
            "http://gw/api/wish/admin/../internal/jobs",
            "http://gw/api/pet/admin/pet/reports",
            "http://gw/api/pet/admin",
            "http://gw/api/pet/admin/",
            "http://gw/api/pet/admin/pet/operations",
            "http://gw/api/pet/ADMIN/pet/wall/messages",
            "http://gw/api/pet//admin//configs/jobs",
            "http://gw/api/pet/internal/anything",
            "http://gw/api/pet/INTERNAL/jobs/scan",
            "http://gw/api/pet/admin%2Fpet/reports",
            "http://gw/api/pet/%61dmin/pet/dashboard",
            "http://gw/api/pet/admin;x=1/pet/reports",
            "http://gw/api/pet/./admin/pet/reports",
            "http://gw/api/pet/admin/../internal/jobs"
    })
    @DisplayName("wish/pet 管理端/内部端点全形态外部访问一律 404")
    void blockedPaths_return404(String uri) {
        MockServerWebExchange exchange = exchange(uri);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://gw/api/wish/wishes",
            "http://gw/api/wish/wishes/123",
            "http://gw/api/wish/home",
            "http://gw/api/wish/map/wishes",
            "http://gw/api/wish/categories",
            "http://gw/api/pet/me",
            "http://gw/api/pet/pets",
            "http://gw/api/pet/shop/items",
            "http://gw/api/pet/public/123",
            "http://gw/api/admin/wishes",
            "http://gw/api/community/posts"
    })
    @DisplayName("普通用户路径与管理端正式路径不阻断，进入后续链")
    void publicPaths_passThrough(String uri) {
        MockServerWebExchange exchange = exchange(uri);
        assertThat(exchange.getResponse().getStatusCode()).as("path %s", uri).isNull();
    }

    @Test
    @DisplayName("404 响应为标准信封")
    void blockedResponse_usesErrorEnvelope() {
        MockServerWebExchange exchange = exchange("http://gw/api/pet/internal/jobs/overdue-scan");
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .startsWith("application/json");
    }

    @ParameterizedTest
    @CsvSource({
            "/api/wish/admin/wishes, true",
            "/api/wish/admin, true",
            "/api/wish/administration, false",
            "/api/wish/internal/jobs, true",
            "/api/wish/internalx, false",
            "/api/wish/wishes, false",
            "/api/pet/admin/pet/reports, true",
            "/api/pet/admin, true",
            "/api/pet/administration, false",
            "/api/pet/internal/jobs, true",
            "/api/pet/internalx, false",
            "/api/pet/me, false"
    })
    @DisplayName("前缀匹配必须落在完整段边界上（入参为规范化后路径）")
    void isBlocked_respectsSegmentBoundary(String path, boolean expected) {
        assertThat(ServiceInternalRouteBlockFilter.isBlocked(path)).isEqualTo(expected);
    }
}
