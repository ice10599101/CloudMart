package com.cloudmart.pet.config;

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
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * SEC-01 用户身份过滤器：用户身份只能由可验证的 RS256 JWT 建立（mall-auth 签发，
 * 网关透传 Authorization），网关注入的 {@code X-User-Id} 不再作为身份源。
 *
 * <p>验签规则与网关 {@code JwtAuthenticationFilter} 对齐：拒绝未签名令牌（alg=none）、
 * 按 kid 匹配 JWKS 公钥验签、拒绝过期令牌；任一失败不建立身份——受保护端点 401，
 * 公开端点保持匿名语义（permitAll 不依赖身份）。</p>
 *
 * <p>与 mall-wish 的差异：验签成功后用 {@link UserIdAuthorityRequestWrapper} 把
 * {@code X-User-Id} 头重写为已验证的 JWT subject——服务端直连场景下外部伪造的
 * X-User-Id 头不再能改变业务归属（各 Controller 继续按头读取，无需改动）。
 * 用户令牌绝不产生 INTERNAL 角色；服务调用方身份由
 * {@link PetServiceTokenAuthenticationFilter} 用独立的服务令牌建立。</p>
 */
public class PetJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PetJwtAuthenticationFilter.class);

    static final String ROLE_USER = "ROLE_USER";

    private final JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource;

    public PetJwtAuthenticationFilter(String jwksUri) {
        try {
            this.jwkSource = new RemoteJWKSet<>(new URL(jwksUri));
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("pet.security.jwks-uri 配置非法: " + jwksUri, e);
        }
    }

    /** 测试用构造：注入受控 JWKSource，避免测试期间访问网络。 */
    PetJwtAuthenticationFilter(JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource) {
        this.jwkSource = jwkSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JWT jwt = JWTParser.parse(authorization.substring(7));
                if (jwt instanceof SignedJWT signedJWT && verifySignature(signedJWT)
                        && !isExpired(jwt.getJWTClaimsSet())) {
                    String userId = jwt.getJWTClaimsSet().getSubject();
                    UsernamePasswordAuthenticationToken authentication =
                            UsernamePasswordAuthenticationToken.authenticated(
                                    userId, null, List.of(new SimpleGrantedAuthority(ROLE_USER)));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    // X-User-Id 头以验证过的 JWT subject 为准，覆盖外部传入值
                    request = new UserIdAuthorityRequestWrapper(request, userId);
                } else {
                    log.warn("[SEC-01 REJECT] 用户 JWT 验签或有效期校验失败: {}", request.getRequestURI());
                }
            } catch (ParseException e) {
                log.warn("[SEC-01 REJECT] 用户 JWT 解析失败: {} reason={}",
                        request.getRequestURI(), e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean verifySignature(SignedJWT signedJWT) {
        try {
            if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
                return false;
            }
            String kid = signedJWT.getHeader().getKeyID();
            List<JWK> keys = jwkSource.get(
                    new JWKSelector(new JWKMatcher.Builder().keyID(kid).build()), null);
            if (keys == null || keys.isEmpty()) {
                return false;
            }
            JWSVerifier verifier = new RSASSAVerifier(keys.get(0).toRSAKey());
            return signedJWT.verify(verifier);
        } catch (Exception e) {
            log.warn("[SEC-01 REJECT] 用户 JWT 验签异常: {}", e.getMessage());
            return false;
        }
    }

    private boolean isExpired(JWTClaimsSet claims) {
        Date expiration = claims.getExpirationTime();
        return expiration != null && expiration.before(new Date());
    }

    /** 把 {@code X-User-Id} 头固定为已验证 subject 的只读请求包装。 */
    private static final class UserIdAuthorityRequestWrapper extends HttpServletRequestWrapper {

        private final String verifiedUserId;

        private UserIdAuthorityRequestWrapper(HttpServletRequest request, String verifiedUserId) {
            super(request);
            this.verifiedUserId = verifiedUserId;
        }

        @Override
        public String getHeader(String name) {
            if (SecurityConstants.USER_ID_HEADER.equalsIgnoreCase(name)) {
                return verifiedUserId;
            }
            return super.getHeader(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            if (SecurityConstants.USER_ID_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(verifiedUserId));
            }
            return super.getHeaders(name);
        }
    }
}
