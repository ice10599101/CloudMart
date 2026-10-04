package com.cloudmart.wish.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T02 服务令牌路径映射守护：wish 本地 PATH_REQUIREMENTS 迁入 application.yml 的
 * {@code cloudmart.security.service-token-paths} 后，该映射没有代码载体可编译期
 * 校验——本测试直接读取 yml，防止前缀/签发方/能力域拼写漂移导致内部调用 401。
 */
@DisplayName("WishServiceTokenPathMapping yml 守护（T02）")
class WishServiceTokenPathMappingTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> securityProperties() throws IOException {
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> cloudmart = (Map<String, Object>) root.get("cloudmart");
            assertThat(cloudmart).as("application.yml 缺少 cloudmart 配置").isNotNull();
            Map<String, Object> security = (Map<String, Object>) cloudmart.get("security");
            assertThat(security).as("application.yml 缺少 cloudmart.security 配置").isNotNull();
            return security;
        }
    }

    @Test
    @DisplayName("service-id 与 JWKS/密钥引用存在")
    void serviceIdConfigured() throws IOException {
        Map<String, Object> security = securityProperties();

        assertThat(security.get("service-id")).isEqualTo("mall-wish");
        assertThat(String.valueOf(security.get("service-token-secret")))
                .contains("wish.security.service-token-secret");
        assertThat(String.valueOf(security.get("jwks-uri")))
                .contains("wish.security.jwks-uri");
    }

    @Test
    @DisplayName("五条路径映射与原 wish-local PATH_REQUIREMENTS 一致")
    void pathMappings_matchFormerLocalFilter() throws IOException {
        Map<String, Object> security = securityProperties();
        Object pathsObj = security.get("service-token-paths");
        assertThat(pathsObj).isInstanceOf(List.class);
        List<Map<String, Object>> paths = castList(pathsObj);

        Map<String, Map<String, Object>> byPrefix = new java.util.LinkedHashMap<>();
        for (Map<String, Object> path : paths) {
            byPrefix.put(String.valueOf(path.get("prefix")), path);
        }

        assertThat(byPrefix.keySet()).containsExactlyInAnyOrder(
                "/admin", "/internal/jobs", "/internal/tree-env",
                "/internal/pet-support", "/internal/account-erasure");

        assertThat(castList(byPrefix.get("/admin").get("issuers"))).isEqualTo(List.of("mall-admin"));
        assertThat(byPrefix.get("/admin").get("scope")).isEqualTo("wish:admin");

        assertThat(castList(byPrefix.get("/internal/jobs").get("issuers"))).isEqualTo(List.of("mall-job"));
        assertThat(byPrefix.get("/internal/jobs").get("scope")).isEqualTo("wish:jobs");

        assertThat(castList(byPrefix.get("/internal/tree-env").get("issuers"))).isEqualTo(List.of("mall-job"));
        assertThat(byPrefix.get("/internal/tree-env").get("scope")).isEqualTo("wish:jobs");

        assertThat(castList(byPrefix.get("/internal/pet-support").get("issuers"))).isEqualTo(List.of("mall-pet"));
        assertThat(byPrefix.get("/internal/pet-support").get("scope")).isEqualTo("wish:pet");

        assertThat(castList(byPrefix.get("/internal/account-erasure").get("issuers"))).isEqualTo(List.of("mall-user"));
        assertThat(byPrefix.get("/internal/account-erasure").get("scope")).isEqualTo("wish:erasure");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
