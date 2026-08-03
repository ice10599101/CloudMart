package com.cloudmart.auth.util;

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
import java.util.Set;
import java.util.UUID;

@Component
public class JwtProvider {

    private final RSAKey rsaKey;
    private final long accessTokenExpiration;

    public JwtProvider(RSAKey rsaKey,
                       @Value("${auth.jwt.access-token-expiration:900}") long accessTokenExpiration) {
        this.rsaKey = rsaKey;
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String generateAccessToken(Long userId, String scope) {
        try {
            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(accessTokenExpiration);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("scope", scope)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiration))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    claims);

            JWSSigner signer = new RSASSASigner(rsaKey.toRSAPrivateKey());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate access token", e);
        }
    }

    public String generateAccessToken(Long userId, String role, Set<String> permissions) {
        return generateAccessToken(userId, role, permissions, null, null);
    }

    public String generateAccessToken(Long userId, String role, Set<String> permissions,
                                       String username, Long deptId) {
        try {
            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(accessTokenExpiration);

            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("scope", role)
                    .claim("perms", permissions != null ? String.join(",", permissions) : "")
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiration));

            if (username != null) {
                builder.claim("username", username);
            }
            if (deptId != null) {
                builder.claim("deptId", deptId.toString());
            }

            JWTClaimsSet claims = builder.build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    claims);

            JWSSigner signer = new RSASSASigner(rsaKey.toRSAPrivateKey());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate access token", e);
        }
    }
}
