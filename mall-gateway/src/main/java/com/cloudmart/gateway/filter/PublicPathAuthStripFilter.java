package com.cloudmart.gateway.filter;

import com.cloudmart.common.constant.SecurityConstants;
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

import java.util.List;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicPathAuthStripFilter implements WebFilter {

    private static final Set<String> ANY_METHOD_PUBLIC_PREFIXES = Set.of(
            "/api/auth/",
            "/api/user/users/register",
            "/api/payment/payments/callback",
            "/api/file/uploads/",
            "/api/gen/preview",
            "/api/gen/download",
            "/actuator/"
    );

    private static final List<String> GET_ONLY_PUBLIC_PREFIXES = List.of(
            "/api/product/products/search",
            "/api/product/categories",
            "/api/product/reviews/",
            "/api/product/products/",
            "/api/coupon/coupon-templates",
            "/api/seckill/activities",
            "/api/seckill/products/activity/",
            "/api/live/rooms",
            "/api/marketing/group/activities",
            "/api/marketing/group/orders",
            "/api/community/users/recommend",
            "/api/community/posts",
            "/api/community/topics",
            "/api/community/tags",
            "/api/community/search"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        if (isPublicPath(path, method)) {
            ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public HttpHeaders getHeaders() {
                    HttpHeaders filtered = new HttpHeaders();
                    super.getHeaders().forEach((name, values) -> {
                        if (!HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                            filtered.addAll(name, values);
                        }
                    });
                    filtered.set(SecurityConstants.INTERNAL_CALL_HEADER, "true");
                    return filtered;
                }
            };
            exchange = exchange.mutate().request(decoratedRequest).build();
        }
        return chain.filter(exchange);
    }

    private boolean isPublicPath(String path, HttpMethod method) {
        for (String prefix : ANY_METHOD_PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        if (method == HttpMethod.GET) {
            for (String prefix : GET_ONLY_PUBLIC_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
            if (path.matches("/api/product/products/\\d+")) {
                return true;
            }
        }
        return false;
    }
}
