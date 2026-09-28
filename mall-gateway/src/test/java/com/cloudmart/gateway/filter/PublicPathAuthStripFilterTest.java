package com.cloudmart.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公开路径 Authorization 处理边界（SEC-01 语义）：
 * 1. 与身份完全无关的公开路径（register/callback 等）：剥离 Authorization
 * 2. 匿名可读、登录个性化的 GET 公开路径（帖子详情/搜索/商品等）：过滤器不做任何改写，
 *    Authorization 保留给 {@link JwtAuthenticationFilter} 验签并注入 X-User-Id
 * 3. 任何路径都不再注入 X-Internal-Call：服务间身份只由 X-Service-Token 签名令牌建立
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
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, ex -> {
            captured.set(ex);
            return Mono.empty();
        }).block();
        if (captured.get() != null) {
            exchange = captured.get();
        }
    }

    @Test
    @DisplayName("SEC-02：/api/auth/logout 保留 Authorization（登出需要令牌撤销会话）")
    void logoutPath_keepsAuthorization() {
        run(MockServerHttpRequest.post("/api/auth/logout")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
    }

    @Test
    @DisplayName("注册等身份无关路径：剥离 Authorization，不注入内部调用标记")
    void registerPath_stripsAuthorization() {
        run(MockServerHttpRequest.post("/api/user/users/register")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isNull();
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Call")).isNull();
    }

    @Test
    @DisplayName("支付回调等身份无关路径：剥离 Authorization")
    void callbackPath_stripsAuthorization() {
        run(MockServerHttpRequest.post("/api/payment/payments/callback")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isNull();
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Call")).isNull();
    }

    @Test
    @DisplayName("帖子详情（匿名可读+登录个性化）：不改写，Authorization 保留")
    void publicPostDetail_keepsAuthorization() {
        run(MockServerHttpRequest.get("/api/community/posts/123")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Call")).isNull();
    }

    @Test
    @DisplayName("drafts/liked 语义私有路径：不改写，Authorization 保留（原 BUG#33 回归）")
    void draftsPath_keepsAuthorization() {
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
    }

    @Test
    @DisplayName("非公开路径：不做任何改写")
    void protectedPath_untouched() {
        run(MockServerHttpRequest.post("/api/order/orders")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Call")).isNull();
    }
}
