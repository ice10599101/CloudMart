package com.cloudmart.auth.controller;

import com.cloudmart.common.exception.BusinessException;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "JWKS", description = "JSON Web Key Set端点，用于JWT公钥分发")
@RestController
public class JwksController {

    private final JWKSource<SecurityContext> jwkSource;

    public JwksController(JWKSource<SecurityContext> jwkSource) {
        this.jwkSource = jwkSource;
    }

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        try {
            JWKSelector selector = new JWKSelector(new JWKMatcher.Builder().build());
            List<com.nimbusds.jose.jwk.JWK> keys = jwkSource.get(selector, null);
            return new JWKSet(keys).toJSONObject();
        } catch (Exception e) {
            throw new BusinessException("JWK_LOAD_FAILED", "获取JWK失败", e);
        }
    }
}
