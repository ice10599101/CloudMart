package com.cloudmart.wish.config;

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
import java.util.Date;
import java.util.List;

/**
 * B01 用户身份过滤器：用户身份只能由可验证的 RS256 JWT 建立（mall-auth 签发，
 * 网关透传 Authorization），网关注入的 {@code X-User-Id} 不再作为身份源。
 *
 * <p>验签规则与网关 {@code JwtAuthenticationFilter} 对齐：拒绝未签名令牌（alg=none）、
 * 按 kid 匹配 JWKS 公钥验签、拒绝过期令牌；任一失败不建立身份——受保护端点 401，
 * 公开端点保持匿名语义（permitAll 不依赖身份）。</p>
 *
 * <p>用户令牌绝不产生 INTERNAL 角色；服务调用方身份由
 * {@link ServiceTokenAuthenticationFilter} 用独立的服务令牌建立。</p>
 */
public class WishJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WishJwtAuthenticationFilter.class);

    static final String ROLE_USER = "ROLE_USER";

    private final JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource;

    public WishJwtAuthenticationFilter(String jwksUri) {
        try {
            this.jwkSource = new RemoteJWKSet<>(new URL(jwksUri));
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("wish.security.jwks-uri 配置非法: " + jwksUri, e);
        }
    }

    /** 测试用构造：注入受控 JWKSource，避免测试期间访问网络。 */
    WishJwtAuthenticationFilter(JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource) {
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
                } else {
                    log.warn("[B01 REJECT] 用户 JWT 验签或有效期校验失败: {}", request.getRequestURI());
                }
            } catch (ParseException e) {
                log.warn("[B01 REJECT] 用户 JWT 解析失败: {} reason={}",
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
            log.warn("[B01 REJECT] 用户 JWT 验签异常: {}", e.getMessage());
            return false;
        }
    }

    private boolean isExpired(JWTClaimsSet claims) {
        Date expiration = claims.getExpirationTime();
        return expiration != null && expiration.before(new Date());
    }
}
