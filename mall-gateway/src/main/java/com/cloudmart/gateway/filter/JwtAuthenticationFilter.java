package com.cloudmart.gateway.filter;

import com.cloudmart.common.constant.SecurityConstants;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String[] HEADERS_TO_STRIP = {
            SecurityConstants.USER_ID_HEADER,
            SecurityConstants.INTERNAL_CALL_HEADER,
            SecurityConstants.ADMIN_ROLE_HEADER,
            SecurityConstants.ADMIN_PERMISSIONS_HEADER,
            SecurityConstants.ADMIN_DEPT_ID_HEADER,
            SecurityConstants.ADMIN_USERNAME_HEADER
    };

    private static final Set<String> ANY_METHOD_PUBLIC_PREFIXES = Set.of(
            "/api/auth/",
            "/api/user/users/register",
            "/api/user/users/validate",
            "/api/payment/payments/callback",
            "/api/file/uploads/",
            "/api/gen/preview",
            "/api/gen/download"
    );

    private static final Set<String> GET_ONLY_PUBLIC_PREFIXES = Set.of(
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
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(builder -> builder
                        .headers(headers -> {
                            for (String header : HEADERS_TO_STRIP) {
                                headers.remove(header);
                            }
                        }))
                .build();

        String path = exchange.getRequest().getPath().value();
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authorization != null && authorization.startsWith(SecurityConstants.BEARER_PREFIX)) {
            String tokenValue = authorization.substring(SecurityConstants.BEARER_PREFIX.length());
            try {
                JWT jwt = JWTParser.parse(tokenValue);
                var claims = jwt.getJWTClaimsSet();

                String userId = claims.getSubject();
                String scope = claims.getStringClaim("scope");
                String perms = claims.getStringClaim("perms");
                String username = claims.getStringClaim("username");
                String deptId = claims.getStringClaim("deptId");

                final ServerWebExchange finalExchange = sanitizedExchange;
                sanitizedExchange = finalExchange.mutate()
                        .request(builder -> builder
                                .header(SecurityConstants.USER_ID_HEADER, userId)
                                .header(SecurityConstants.INTERNAL_CALL_HEADER, "true")
                                .headers(headers -> {
                                    if (scope != null) {
                                        headers.add(SecurityConstants.ADMIN_ROLE_HEADER, scope);
                                    }
                                    if (perms != null) {
                                        headers.add(SecurityConstants.ADMIN_PERMISSIONS_HEADER, perms);
                                    }
                                    if (username != null) {
                                        headers.add(SecurityConstants.ADMIN_USERNAME_HEADER, username);
                                    }
                                    if (deptId != null) {
                                        headers.add(SecurityConstants.ADMIN_DEPT_ID_HEADER, deptId);
                                    }
                                }))
                        .build();
            } catch (ParseException e) {
                log.warn("JWT parse failed for path {}: {}", path, e.getMessage());
            }
        }

        if (isPublicPath(path, exchange.getRequest().getMethod())) {
            log.info("[PUBLIC PATH] {} matched public path, adding INTERNAL_CALL_HEADER", path);
            ServerHttpRequest request = sanitizedExchange.getRequest();
            if (request.getHeaders().getFirst(SecurityConstants.INTERNAL_CALL_HEADER) == null) {
                sanitizedExchange = sanitizedExchange.mutate()
                        .request(builder -> builder
                                .header(SecurityConstants.INTERNAL_CALL_HEADER, "true"))
                        .build();
            }
        }

        return chain.filter(sanitizedExchange);
    }

    private boolean isPublicPath(String path, org.springframework.http.HttpMethod method) {
        if (path == null) return false;
        for (String prefix : ANY_METHOD_PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        if (method == org.springframework.http.HttpMethod.GET) {
            for (String prefix : GET_ONLY_PUBLIC_PREFIXES) {
                if (path.startsWith(prefix)) return true;
            }
            if (path.matches("/api/product/products/\\d+")) return true;
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1000;
    }
}
