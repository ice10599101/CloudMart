package com.cloudmart.common.config;

import com.cloudmart.common.security.CloudmartSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ENG-01：生产配置启动自检（fail-fast）——prod profile 下拒绝不安全启动：
 * <ul>
 *   <li>服务令牌共享密钥未配置或弱（&lt;32 字节）——入站内部端点会 fail-closed 全拒，
 *       出站调用被拒，服务不完整，宁可拒绝启动；</li>
 *   <li>默认占位密钥（如 123456/changeit/secret）。</li>
 * </ul>
 * 经 {@code @Profile("prod")} 只在生产生效；开发环境不受影响。
 */
@Configuration
@org.springframework.context.annotation.Profile("prod")
public class ProdStartupGuard implements org.springframework.beans.factory.InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProdStartupGuard.class);

    private static final List<String> FORBIDDEN_SECRETS = List.of(
            "123456", "changeit", "secret", "password", "changeme", "test");

    private final CloudmartSecurityProperties properties;

    public ProdStartupGuard(CloudmartSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        String secret = properties.getServiceTokenSecret();
        if (secret == null || secret.isBlank()) {
            fail("cloudmart.security.service-token-secret 未配置（环境变量 CLOUDMART_SERVICE_TOKEN_SECRET）");
        }
        if (secret.length() < 32) {
            fail("service-token-secret 强度不足（≥32 字节），当前 " + secret.length());
        }
        String lower = secret.toLowerCase();
        for (String forbidden : FORBIDDEN_SECRETS) {
            if (lower.contains(forbidden)) {
                fail("service-token-secret 含已知弱口令片段，禁止在生产使用");
            }
        }
        log.info("[ENG01] 生产启动自检通过（service-id={}, secret 强度 {} 字符）",
                properties.getServiceId(), secret.length());
    }

    private void fail(String reason) {
        log.error("[ENG01] 生产配置自检失败，拒绝启动: {}", reason);
        throw new IllegalStateException("生产配置自检失败: " + reason);
    }
}
