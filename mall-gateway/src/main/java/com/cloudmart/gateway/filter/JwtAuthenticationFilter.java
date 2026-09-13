package com.cloudmart.gateway.filter;

import com.cloudmart.common.constant.SecurityConstants;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
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

import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** JWKS 公钥来源（mall-auth 签发 RS256）；验签失败一律拒绝注入身份 */
    private final JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource;

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

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthenticationFilter(
            @Value("${gateway.jwt.jwks-uri:http://127.0.0.1:9001/oauth2/jwks}") String jwksUri) {
        try {
            this.jwkSource = new RemoteJWKSet<>(new URL(jwksUri));
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("gateway.jwt.jwks-uri 配置非法: " + jwksUri, e);
        }
    }

    /** 测试用构造：注入受控的 JWKSource */
    JwtAuthenticationFilter(JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource) {
        this.jwkSource = jwkSource;
    }

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
            "/api/community/search",
            "/api/wish/wishes",
            "/api/wish/categories",
            "/api/wish/home",
            "/api/wish/map"
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

                // 安全防线 1：拒绝无签名的明文 JWT（alg=none 可被任意伪造）
                if (!(jwt instanceof SignedJWT signedJWT)) {
                    log.warn("[AUTH REJECT] 未签名的 JWT（alg=none），拒绝注入身份: {}", path);
                    return chain.filter(sanitizedExchange);
                }
                // 安全防线 2：RS256 签名验证（公钥来自 mall-auth JWKS）
                if (!verifySignature(signedJWT)) {
                    log.warn("[AUTH REJECT] JWT 签名验证失败: {}", path);
                    return chain.filter(sanitizedExchange);
                }

                JWTClaimsSet claims = jwt.getJWTClaimsSet();

                // 安全防线 3：拒绝过期 token（parse 不校验 exp，此处显式校验）
                Date expiration = claims.getExpirationTime();
                if (expiration != null && expiration.before(new Date())) {
                    log.warn("[AUTH REJECT] JWT 已过期: {}", path);
                    return chain.filter(sanitizedExchange);
                }

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

    /** RS256 签名验证：按 kid 从 JWKS 匹配公钥；无匹配 key 或验证异常视为失败 */
    private boolean verifySignature(SignedJWT signedJWT) {
        try {
            String kid = signedJWT.getHeader().getKeyID();
            JWKSelector selector = new JWKSelector(
                    new JWKMatcher.Builder().keyID(kid).build());
            List<JWK> keys = jwkSource.get(selector, null);
            if (keys == null || keys.isEmpty()) {
                log.warn("[AUTH REJECT] JWKS 中无匹配 kid={} 的公钥", kid);
                return false;
            }
            JWSVerifier verifier = new RSASSAVerifier(keys.get(0).toRSAKey());
            return signedJWT.verify(verifier);
        } catch (Exception e) {
            log.warn("[AUTH REJECT] JWT 验签异常: {}", e.getMessage());
            return false;
        }
    }

    /** 以公开前缀 /api/community/posts 开头、但实际需要登录身份的子路径：不算公开路径，
     * 匿名访问时服务端必须 401，带有效令牌时必须注入 X-User-Id */
    private static final Set<String> COMMUNITY_POSTS_IDENTITY_REQUIRED_PREFIXES = Set.of(
            "/api/community/posts/drafts",
            "/api/community/posts/liked"
    );

    private boolean isPublicPath(String path, org.springframework.http.HttpMethod method) {
        if (path == null) return false;
        for (String prefix : COMMUNITY_POSTS_IDENTITY_REQUIRED_PREFIXES) {
            if (path.startsWith(prefix)) return false;
        }
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
