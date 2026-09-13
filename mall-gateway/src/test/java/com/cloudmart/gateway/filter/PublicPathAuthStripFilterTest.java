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
 * 公开路径 Authorization 处理边界（语义分两档）：
 * 1. 与身份完全无关的公开路径（register/callback 等）：剥离 Authorization
 * 2. 匿名可读、登录个性化的 GET 公开路径（帖子详情/搜索/商品等）：保留 Authorization
 *    ——公开详情页的 isLiked/isCollected 个性化依赖有效令牌注入身份，
 *      剥离会让登录用户永远显示未点赞（BUG：已点赞再点报「已点赞」）
 * 3. /posts/drafts、/posts/liked：语义私有，无论哪档都必须保留 Authorization
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
    @DisplayName("注册等身份无关路径：剥离 Authorization 并标记内部调用")
    void registerPath_stripsAuthorization() {
        run(MockServerHttpRequest.post("/api/user/users/register")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isNull();
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Call")).isEqualTo("true");
    }

    @Test
    @DisplayName("帖子详情（匿名可读+登录个性化）：保留 Authorization")
    void publicPostDetail_keepsAuthorization() {
        run(MockServerHttpRequest.get("/api/community/posts/123")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Call")).isEqualTo("true");
    }

    @Test
    @DisplayName("帖子列表/搜索等 GET 公开路径：保留 Authorization（搜索历史等个性化）")
    void publicSearchPath_keepsAuthorization() {
        run(MockServerHttpRequest.get("/api/community/search?keyword=x")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
    }

    @Test
    @DisplayName("drafts/liked 语义私有路径：保留 Authorization（原 BUG#33 回归）")
    void draftsPath_keepsAuthorization() {
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
    }

    @Test
    @DisplayName("非公开路径：不做任何改写")
    void protectedPath_untouched() {
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer token")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
    }
}
