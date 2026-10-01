package com.cloudmart.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * 公开路径身份剥离过滤器：与身份完全无关的公开接口（登录/注册/支付回调等）剥离
 * Authorization，避免下游误读无关凭据；匿名可读、登录个性化的 GET 公开路径
 * （帖子详情/商品等）保留 Authorization 供 {@link JwtAuthenticationFilter} 注入
 * X-User-Id（帖子详情 isLiked/isCollected 等个性化语义依赖它）。
 *
 * <p>SEC-02：公开路径按逐接口名单剥离，不再对整个 {@code /api/auth/} 前缀生效——
 * {@code /api/auth/logout} 必须保留 Bearer，否则 mall-auth 资源服务器拿不到令牌，
 * 登出永远无法真正撤销会话（仅靠 @AuthenticationPrincipal null 兜底伪成功）。</p>
 *
 * <p>SEC-01：不再注入任何 X-Internal-Call 标记——公开端点在下游以 permitAll
 * 匿名放行，服务间身份只由 X-Service-Token 签名令牌建立。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicPathAuthStripFilter implements WebFilter {

    private static final Set<String> ANY_METHOD_PUBLIC_PREFIXES = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/user/users/register",
            "/api/payment/payment-attempts/mock-callbacks",
            "/actuator/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        if (isIdentityFreePublicPath(path, method)) {
            ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public HttpHeaders getHeaders() {
                    HttpHeaders filtered = new HttpHeaders();
                    super.getHeaders().forEach((name, values) -> {
                        if (!HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                            filtered.addAll(name, values);
                        }
                    });
                    return filtered;
                }
            };
            exchange = exchange.mutate().request(decoratedRequest).build();
        }
        return chain.filter(exchange);
    }

    /** 与身份完全无关的公开路径：剥离 Authorization */
    private boolean isIdentityFreePublicPath(String path, HttpMethod method) {
        for (String prefix : ANY_METHOD_PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
