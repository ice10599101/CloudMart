package com.cloudmart.gateway.filter;

import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.gateway.security.SessionValidator;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 网关 JWT 认证过滤器（SEC-01/03）。
 *
 * <p>校验链（任一失败即拒绝并返回 401 信封，不注入任何身份头）：</p>
 * <ol>
 *   <li>拒绝未签名令牌（alg=none）与强制 RS256；</li>
 *   <li>按 kid 从 JWKS 验签；</li>
 *   <li>完整声明语义：iss/aud/nbf/exp/sub 全部核对；</li>
 *   <li>会话与版本：sid 必须存在于会话账本且 authVersion 与令牌声明一致——
 *      禁用/改密/踢人后旧令牌秒级失效（目标 ≤ 5 秒），刷新无法恢复；</li>
 *   <li>Redis 故障 fail-closed：拒绝认证并告警，绝不放行未校验会话。</li>
 * </ol>
 *
 * <p>验签成功后注入身份数据头（X-User-Id/X-Admin-*）；不再注入 X-Internal-Call——
 * 服务间身份只由 X-Service-Token 短期签名令牌建立。</p>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource;
    private final SessionValidator sessionValidator;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final long clockSkewSeconds;

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
            "/api/payment/payments/callback"
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
            "/api/community/search",
            // 编辑器附件（投票/问卷）：匿名可查看内容中的投票与问卷，提交仍需登录
            "/api/community/polls",
            "/api/community/surveys",
            "/api/wish/wishes",
            "/api/wish/categories",
            "/api/wish/home",
            "/api/wish/map",
            "/api/wish/tree-env"
    );

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthenticationFilter(
            @Value("${gateway.jwt.jwks-uri:http://127.0.0.1:9001/oauth2/jwks}") String jwksUri,
            @Value("${gateway.jwt.expected-issuer:cloudmart-auth}") String expectedIssuer,
            @Value("${gateway.jwt.expected-audience:cloudmart-api}") String expectedAudience,
            @Value("${gateway.jwt.clock-skew-seconds:30}") long clockSkewSeconds,
            SessionValidator sessionValidator) {
        this(buildJwkSource(jwksUri), sessionValidator, expectedIssuer, expectedAudience, clockSkewSeconds);
    }

    /** 测试用构造：注入受控的 JWKSource，会话校验可传 null（跳过会话层） */
    JwtAuthenticationFilter(JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource,
                            SessionValidator sessionValidator) {
        this(jwkSource, sessionValidator, "cloudmart-auth", "cloudmart-api", 30);
    }

    private JwtAuthenticationFilter(JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource,
                                    SessionValidator sessionValidator,
                                    String expectedIssuer, String expectedAudience, long clockSkewSeconds) {
        this.jwkSource = jwkSource;
        this.sessionValidator = sessionValidator;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.clockSkewSeconds = clockSkewSeconds;
    }

    private static JWKSource<com.nimbusds.jose.proc.SecurityContext> buildJwkSource(String jwksUri) {
        try {
            return new RemoteJWKSet<>(new URL(jwksUri));
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("gateway.jwt.jwks-uri 配置非法: " + jwksUri, e);
        }
    }

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
        String sid = null;
        String authVersion = null;
        String userId = null;
        String scope = null;
        String perms = null;
        String username = null;
        String deptId = null;

        if (authorization == null || !authorization.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return chain.filter(sanitizedExchange);
        }

        JWTClaimsSet claims;
        try {
            String tokenValue = authorization.substring(SecurityConstants.BEARER_PREFIX.length());
            JWT jwt = JWTParser.parse(tokenValue);

            // 防线 1：拒绝无签名的明文 JWT（alg=none 可被任意伪造）
            if (!(jwt instanceof SignedJWT signedJWT)) {
                log.warn("[AUTH REJECT] 未签名的 JWT（alg=none），拒绝注入身份: {}", path);
                return chain.filter(sanitizedExchange);
            }
            // 防线 2：RS256 签名验证（公钥来自 mall-auth JWKS）
            if (!verifySignature(signedJWT)) {
                log.warn("[AUTH REJECT] JWT 签名验证失败: {}", path);
                return chain.filter(sanitizedExchange);
            }
            claims = jwt.getJWTClaimsSet();

            // 防线 3：完整声明语义校验（SEC-03）
            String rejectReason = validateClaims(claims);
            if (rejectReason != null) {
                log.warn("[AUTH REJECT] {}: {}", rejectReason, path);
                return chain.filter(sanitizedExchange);
            }

            // 防线 4：会话有效性 + 认证状态版本（Redis 权威记录，fail-closed）
            sid = claims.getStringClaim("sid");
            authVersion = String.valueOf(claims.getLongClaim("authVersion"));
            userId = claims.getSubject();
            scope = claims.getStringClaim("scope");
            perms = claims.getStringClaim("perms");
            username = claims.getStringClaim("username");
            deptId = claims.getStringClaim("deptId");
        } catch (ParseException e) {
            log.warn("[AUTH REJECT] JWT 解析失败 for path {}: {}", path, e.getMessage());
            return chain.filter(sanitizedExchange);
        }

        final String fSid = sid;
        final String fAuthVersion = authVersion;
        final String fUserId = userId;
        final String fScope = scope;
        final String fPerms = perms;
        final String fUsername = username;
        final String fDeptId = deptId;

        return sessionValidator.isSessionValid(fSid, fAuthVersion)
                .flatMap(valid -> {
                    if (!valid) {
                        log.warn("[AUTH REJECT] 会话无效或认证状态版本过期 sid={}: {}",
                                fSid, path);
                        return unauthorized(exchange.getResponse());
                    }
                    ServerWebExchange enriched = sanitizedExchange.mutate()
                            .request(builder -> builder
                                    .header(SecurityConstants.USER_ID_HEADER, fUserId)
                                    .headers(headers -> {
                                        if (fScope != null) {
                                            headers.add(SecurityConstants.ADMIN_ROLE_HEADER, fScope);
                                        }
                                        if (fPerms != null) {
                                            headers.add(SecurityConstants.ADMIN_PERMISSIONS_HEADER, fPerms);
                                        }
                                        if (fUsername != null) {
                                            headers.add(SecurityConstants.ADMIN_USERNAME_HEADER, fUsername);
                                        }
                                        if (fDeptId != null) {
                                            headers.add(SecurityConstants.ADMIN_DEPT_ID_HEADER, fDeptId);
                                        }
                                    }))
                            .build();
                    return chain.filter(enriched);
                })
                .onErrorResume(e -> {
                    // fail-closed：Redis 故障时拒绝认证（不放行未校验会话）
                    log.error("[AUTH REJECT] 会话校验服务异常（fail-closed）: {}", e.getMessage());
                    return unauthorized(exchange.getResponse());
                });
    }

    /** @return null 表示全部通过；否则返回拒绝原因 */
    private String validateClaims(JWTClaimsSet claims) throws ParseException {
        String issuer = claims.getIssuer();
        if (!expectedIssuer.equals(issuer)) {
            return "iss 不匹配: " + issuer;
        }
        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(expectedAudience)) {
            return "aud 不匹配";
        }
        Date notBefore = claims.getNotBeforeTime();
        if (notBefore != null && notBefore.getTime() - clockSkewSeconds * 1000 > System.currentTimeMillis()) {
            return "nbf 在未来";
        }
        Date expiration = claims.getExpirationTime();
        if (expiration == null) {
            return "缺少 exp";
        }
        if (expiration.getTime() + clockSkewSeconds * 1000 <= System.currentTimeMillis()) {
            return "JWT 已过期";
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            return "缺少主体声明";
        }
        String sid = claims.getStringClaim("sid");
        if (sid == null || sid.isBlank()) {
            return "缺少会话声明 sid";
        }
        if (claims.getClaim("authVersion") == null) {
            return "缺少认证状态版本 authVersion";
        }
        String subjectType = claims.getStringClaim("subjectType");
        String scope = claims.getStringClaim("scope");
        if (subjectType == null || !subjectType.equals(scope)
                || !(Set.of("user", "admin").contains(subjectType))) {
            return "身份域非法: subjectType=" + subjectType;
        }
        return null;
    }

    /** RS256 签名验证：按 kid 从 JWKS 匹配公钥；无匹配 key 或验证异常视为失败 */
    private boolean verifySignature(SignedJWT signedJWT) {
        try {
            if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
                return false;
            }
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

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"success\":false,\"data\":null,\"error\":{\"code\":\"UNAUTHORIZED\","
                + "\"message\":\"登录状态已失效，请重新登录\",\"details\":[]},\"meta\":{}}";
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1000;
    }
}
