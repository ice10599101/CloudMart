package com.cloudmart.auth.util;

import com.cloudmart.auth.service.SubjectType;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 访问令牌签发器（SEC-03）：令牌承载可撤销会话与身份域声明。
 *
 * <p>声明集（在既有 sub/exp/jti 基础上新增）：</p>
 * <ul>
 *   <li>{@code sid}：会话标识——网关按会话键 {@code auth:session_valid:{sid}}
 *       校验会话有效性（禁用/踢人秒级失效的载体）；</li>
 *   <li>{@code authVersion}：签发时的认证状态版本——主体被禁用/改密/改权限时
 *       版本递增，版本不匹配的令牌立即失效；</li>
 *   <li>{@code subjectType}：身份域（user/admin），与 scope 同源；</li>
 *   <li>{@code iss}/{@code aud}/{@code nbf}：完整 JWT 语义校验所必需，
 *       网关逐项核对，缺失或不匹配一律拒绝。</li>
 * </ul>
 */
@Component
public class JwtProvider {

    public static final String ISSUER = "cloudmart-auth";
    public static final String AUDIENCE = "cloudmart-api";

    private final RSAKey rsaKey;
    private final long accessTokenExpiration;

    public JwtProvider(RSAKey rsaKey,
                       @Value("${auth.jwt.access-token-expiration:900}") long accessTokenExpiration) {
        this.rsaKey = rsaKey;
        this.accessTokenExpiration = accessTokenExpiration;
    }

    /** 签发令牌所需的主体/会话上下文 */
    public record TokenPrincipal(SubjectType subjectType, Long subjectId, String sid, long authVersion) {
    }

    /** 普通用户令牌（scope=user） */
    public String generateUserAccessToken(TokenPrincipal principal) {
        return sign(principal, null, null, null);
    }

    /** 管理员令牌（scope=admin，携带权限集） */
    public String generateAdminAccessToken(TokenPrincipal principal, Set<String> permissions,
                                           String username, Long deptId) {
        return sign(principal, permissions, username, deptId);
    }

    private String sign(TokenPrincipal principal, Set<String> permissions, String username, Long deptId) {
        try {
            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(accessTokenExpiration);

            String scope = principal.subjectType() == SubjectType.ADMIN ? "admin" : "user";
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience(List.of(AUDIENCE))
                    .subject(principal.subjectId().toString())
                    .claim("scope", scope)
                    .claim("subjectType", scope)
                    .claim("sid", principal.sid())
                    .claim("authVersion", principal.authVersion())
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiration))
                    .notBeforeTime(Date.from(now));
            if (permissions != null) {
                builder.claim("perms", String.join(",", permissions));
            }
            if (username != null) {
                builder.claim("username", username);
            }
            if (deptId != null) {
                builder.claim("deptId", deptId.toString());
            }

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    builder.build());

            JWSSigner signer = new RSASSASigner(rsaKey.toRSAPrivateKey());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate access token", e);
        }
    }
}
