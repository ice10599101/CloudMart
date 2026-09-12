package com.cloudmart.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定公开路径 Authorization 剥离边界：
 * 1. 公开路径剥离客户端 Authorization（公开接口不接受身份语义）
 * 2. /posts/drafts、/posts/liked 虽以公开前缀 /api/community/posts 开头，但语义私有，
 *    必须保留 Authorization 供 JwtAuthenticationFilter 注入身份，否则服务端必返 401
 */
class PublicPathAuthStripFilterTest {

    private PublicPathAuthStripFilter filter;
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        filter = new PublicPathAuthStripFilter();
    }

    private void run(MockServerHttpRequest request) {
        exchange = MockServerWebExchange.from(request);
        java.util.concurrent.atomic.AtomicReference<ServerWebExchange> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        filter.filter(exchange, ex -> {
            captured.set(ex);
            return Mono.empty();
        }).block();
        if (captured.get() != null) {
            exchange = captured.get();
        }
    }

    @Test
    void publicPostList_stripsAuthorization() {
        run(MockServerHttpRequest.get("/api/community/posts?page=1")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isNull();
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Call")).isEqualTo("true");
    }

    @Test
    void publicPostDetail_stripsAuthorization() {
        run(MockServerHttpRequest.get("/api/community/posts/123")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isNull();
    }

    @Test
    void draftsPath_keepsAuthorization() {
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
    }

    @Test
    void likedPath_keepsAuthorization() {
        run(MockServerHttpRequest.get("/api/community/posts/liked")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
    }
}
