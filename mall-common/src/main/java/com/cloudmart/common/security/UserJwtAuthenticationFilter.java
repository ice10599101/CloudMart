package com.cloudmart.common.security;

import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.context.AdminSecurityContext;
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
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户/管理员身份过滤器（SEC-01）：身份只能由可验证的 RS256 JWT 建立
 * （mall-auth 签发，网关透传 Authorization），网关注入的 {@code X-User-Id}
 * 不再作为身份源。
 *
 * <p>验签规则与网关 {@code JwtAuthenticationFilter} 对齐并更严格：拒绝未签名令牌
 * （alg=none）、强制 RS256、按 kid 匹配 JWKS 公钥验签、拒绝过期与 nbf 在未来的令牌。
 * 身份域由 {@code scope} 声明决定：</p>
 * <ul>
 *   <li>{@code user}（或缺省）→ ROLE_USER；</li>
 *   <li>{@code admin} → ROLE_ADMIN，并按令牌声明填充 {@link AdminSecurityContext}
 *       （userId/username/deptId/permissions），使 @RequiresPermission 注解在
 *       管理员上下文缺失的模块（如 mall-job/mall-gen）真正生效；</li>
 *   <li>其他取值 → 拒绝建立身份（未知身份域不允许冒充用户）。</li>
 * </ul>
 *
 * <p>防直连伪造：认证成功后用请求包装器把 {@code X-User-Id} 强制改写为令牌主体
 * （sub）——绕过网关直连服务实例时无法借自填头冒充其他用户。用户令牌绝不产生
 * INTERNAL 角色；服务调用方身份由 {@link ServiceTokenAuthenticationFilter} 建立。</p>
 */
public class UserJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserJwtAuthenticationFilter.class);

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private static final String SCOPE_USER = "user";
    private static final String SCOPE_ADMIN = "admin";

    private final JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource;
    private final CloudmartSecurityProperties properties;

    public UserJwtAuthenticationFilter(CloudmartSecurityProperties properties) {
        this.properties = properties;
        try {
            this.jwkSource = new RemoteJWKSet<>(new URL(properties.getJwksUri()));
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("cloudmart.security.jwks-uri 配置非法: "
                    + properties.getJwksUri(), e);
        }
    }

    /** 测试用构造：注入受控 JWKSource，避免测试期间访问网络。 */
    public UserJwtAuthenticationFilter(CloudmartSecurityProperties properties,
                                       JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource) {
        this.properties = properties;
        this.jwkSource = jwkSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest requestToUse = request;
        try {
            String authorization = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
            if (authorization != null && authorization.startsWith(SecurityConstants.BEARER_PREFIX)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                VerifiedIdentity identity = verify(authorization.substring(
                        SecurityConstants.BEARER_PREFIX.length()), request.getRequestURI());
                if (identity != null) {
                    requestToUse = new ForcedUserIdHeaderRequest(request, identity.subject());
                    UsernamePasswordAuthenticationToken authentication =
                            UsernamePasswordAuthenticationToken.authenticated(
                                    identity.subject(), null,
                                    List.of(new SimpleGrantedAuthority(identity.authority())));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    if (identity.adminContext() != null) {
                        AdminSecurityContext.set(identity.adminContext());
                    }
                }
            }
            filterChain.doFilter(requestToUse, response);
        } finally {
            // ThreadLocal 管理权在过滤器自身：无论下游是否注册权限拦截器都不泄漏
            AdminSecurityContext.clear();
        }
    }

    /** @return 校验通过的身份；任何失败返回 null（不建立身份，受保护端点由 401 兜底） */
    private VerifiedIdentity verify(String token, String requestUri) {
        try {
            JWT jwt = JWTParser.parse(token);
            if (!(jwt instanceof SignedJWT signedJWT)) {
                log.warn("[SEC01 REJECT] 未签名的 JWT（alg=none）: {}", requestUri);
                return null;
            }
            if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
                log.warn("[SEC01 REJECT] 非法 JWT 算法: {} path={}",
                        signedJWT.getHeader().getAlgorithm(), requestUri);
                return null;
            }
            String kid = signedJWT.getHeader().getKeyID();
            List<JWK> keys = jwkSource.get(
                    new JWKSelector(new JWKMatcher.Builder().keyID(kid).build()), null);
            if (keys == null || keys.isEmpty()) {
                log.warn("[SEC01 REJECT] JWKS 中无匹配 kid={} 的公钥: {}", kid, requestUri);
                return null;
            }
            JWSVerifier verifier = new RSASSAVerifier(keys.get(0).toRSAKey());
            if (!signedJWT.verify(verifier)) {
                log.warn("[SEC01 REJECT] JWT 签名验证失败: {}", requestUri);
                return null;
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null) {
                log.warn("[SEC01 REJECT] JWT 缺少 exp: {}", requestUri);
                return null;
            }
            long skewSeconds = properties.getClockSkewSeconds();
            if (expiration.getTime() - skewSeconds * 1000L <= System.currentTimeMillis()) {
                log.warn("[SEC01 REJECT] JWT 已过期: {}", requestUri);
                return null;
            }
            Date notBefore = claims.getNotBeforeTime();
            if (notBefore != null
                    && notBefore.getTime() + skewSeconds * 1000L > System.currentTimeMillis()) {
                log.warn("[SEC01 REJECT] JWT nbf 在未来: {}", requestUri);
                return null;
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                log.warn("[SEC01 REJECT] JWT 缺少主体声明: {}", requestUri);
                return null;
            }
            String scope = claims.getStringClaim("scope");
            if (scope == null || scope.isBlank() || SCOPE_USER.equals(scope)) {
                return new VerifiedIdentity(subject, ROLE_USER, null);
            }
            if (SCOPE_ADMIN.equals(scope)) {
                return new VerifiedIdentity(subject, ROLE_ADMIN, buildAdminContext(claims, subject));
            }
            log.warn("[SEC01 REJECT] 未知身份域 scope={}: {}", scope, requestUri);
            return null;
        } catch (ParseException e) {
            log.warn("[SEC01 REJECT] JWT 解析失败: {} reason={}", requestUri, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("[SEC01 REJECT] JWT 验签异常: {} reason={}", requestUri, e.getMessage());
            return null;
        }
    }

    /** 管理员令牌携带的声明与网关注入头同源：perms 为逗号分隔的权限码 */
    private AdminSecurityContext buildAdminContext(JWTClaimsSet claims, String subject) {
        String perms = claims.getStringClaim("perms");
        Set<String> permissions = perms == null || perms.isBlank()
                ? Set.of()
                : new HashSet<>(Arrays.asList(perms.split(",")));
        String username = claims.getStringClaim("username");
        String deptId = claims.getStringClaim("deptId");
        return new AdminSecurityContext(
                Long.valueOf(subject),
                username,
                SCOPE_ADMIN,
                permissions,
                deptId == null || deptId.isBlank() ? null : Long.valueOf(deptId));
    }

    private record VerifiedIdentity(String subject, String authority, AdminSecurityContext adminContext) {
    }

    /** 把 X-User-Id 强制改写为已验证令牌主体，封堵直连服务实例时的身份头伪造 */
    private static final class ForcedUserIdHeaderRequest extends HttpServletRequestWrapper {

        private final String userId;

        ForcedUserIdHeaderRequest(HttpServletRequest request, String userId) {
            super(request);
            this.userId = userId;
        }

        @Override
        public String getHeader(String name) {
            if (SecurityConstants.USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (SecurityConstants.USER_ID_HEADER.equalsIgnoreCase(name)) {
                return java.util.Collections.enumeration(List.of(userId));
            }
            return super.getHeaders(name);
        }
    }
}
