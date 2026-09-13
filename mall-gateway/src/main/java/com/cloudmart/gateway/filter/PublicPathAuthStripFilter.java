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

    /** 以公开前缀 /api/community/posts 开头、但实际需要登录身份的子路径：绝不能剥离 Authorization，
     * 否则 JwtAuthenticationFilter 拿不到令牌、无法注入 X-User-Id，服务端必返 401 */
    private static final Set<String> COMMUNITY_POSTS_IDENTITY_REQUIRED_PREFIXES = Set.of(
            "/api/community/posts/drafts",
            "/api/community/posts/liked"
    );

    /**
     * 公开路径分两类处理：
     * <ul>
     *   <li>ANY_METHOD 公开路径（register/callback 等）：剥离 Authorization——这类接口
     *       与身份完全无关；</li>
     *   <li>GET_ONLY 公开路径（帖子详情/搜索/商品等）：<b>保留</b> Authorization——
     *       这些接口是「匿名可读、登录个性化」语义（如帖子详情 isLiked/isCollected），
     *       剥离会导致登录用户在公开详情页永远显示未点赞/未收藏，再点一次点赞
     *       就会触发后端「已点赞」错误。有效令牌时 JwtAuthenticationFilter 仍会
     *       注入 X-User-Id，客户端伪造的身份头在 JWT 过滤器统一剥离，无伪造面。</li>
     * </ul>
     */
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
                    filtered.set(SecurityConstants.INTERNAL_CALL_HEADER, "true");
                    return filtered;
                }
            };
            exchange = exchange.mutate().request(decoratedRequest).build();
        } else if (isPublicReadablePath(path, method)) {
            // 匿名可读路径：不剥 Authorization（保留个性化），仅标记内部调用
            // （带有效令牌时 JwtAuthenticationFilter 注入 X-User-Id，与 INTERNAL_CALL 并存）
            if (exchange.getRequest().getHeaders().getFirst(SecurityConstants.INTERNAL_CALL_HEADER) == null) {
                exchange = exchange.mutate()
                        .request(builder -> builder.header(SecurityConstants.INTERNAL_CALL_HEADER, "true"))
                        .build();
            }
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

    /** 匿名可读、登录个性化的 GET 公开路径：保留 Authorization 供身份注入 */
    private boolean isPublicReadablePath(String path, HttpMethod method) {
        if (method != HttpMethod.GET) {
            return false;
        }
        for (String prefix : COMMUNITY_POSTS_IDENTITY_REQUIRED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : GET_ONLY_PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return path.matches("/api/product/products/\\d+");
    }
}
