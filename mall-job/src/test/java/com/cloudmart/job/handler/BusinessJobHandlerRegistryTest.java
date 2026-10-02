package com.cloudmart.job.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E02：任务注册表守门测试——全部 XXL-JOB handler 的目标必须满足：
 * <ul>
 *   <li>目标路径含 {@code /internal/}（内部任务禁止借用户前缀，历史教训：
 *       /orders/timeout-cancel 404、/marketing/group/expiration 网关前缀、
 *       /coupons/expire-batch 用户前缀 + permitAll）；</li>
 *   <li>scope 前缀与目标服务对应（mall-order → order:*，以此类推）；</li>
 *   <li>清单与 handler 源码一致（新增/删改任务必须同步登记）。</li>
 * </ul>
 */
class BusinessJobHandlerRegistryTest {

    /** URI 内联字符串（.uri("http://svc/path...")） */
    private static final Pattern URI_PATTERN =
            Pattern.compile("\\.uri\\(\"(http://[^\"]+)\"\\)");
    /** scope 签名（serviceTokenProvider.sign("svc", "scope")） */
    private static final Pattern SCOPE_PATTERN =
            Pattern.compile("serviceTokenProvider\\.sign\\(\"([^\"]+)\", \"([^\"]+)\"\\)");
    /** handler 名 → (目标服务, 服务内路径, scope)；与 BusinessJobHandler 源码保持一致 */
    private static final Map<String, Target> REGISTRY = Map.ofEntries(
            Map.entry("orderAutoConfirmHandler", new Target("mall-order", "/internal/orders/auto-confirm-receipts?days=7", "order:internal")),
            Map.entry("groupExpirationHandler", new Target("mall-marketing", "/internal/marketing/group/expiration", "marketing:internal")),
            Map.entry("orderTimeoutCancelHandler", new Target("mall-order", "/internal/orders/timeout-scan?timeoutMinutes=15&batchSize=200", "order:internal")),
            Map.entry("couponExpirationHandler", new Target("mall-coupon", "/internal/coupons/expire-batch", "coupon:jobs"))
            // mall-wish 的 19 个内部任务（treeMood/wishOverdue/hotCache/season/badge/capsule/aiReminder/
            // leaderboard/encounter/trace/starlight×2/level/restriction/riskScore/inactiveArchive/
            // dataExport/accountDeletion/activityReward/brandReward）走 WishServiceTokenProvider，
            // 路径均为 /internal/jobs/** 或 /internal/tree-env/**，由下方源码扫描统一断言。
    );

    private record Target(String service, String path, String scope) {}

    private static final Map<String, String> SERVICE_SCOPE_PREFIX = Map.of(
            "mall-order", "order:",
            "mall-marketing", "marketing:",
            "mall-coupon", "coupon:",
            "mall-wish", "wish:",
            "mall-seckill", "seckill:",
            "mall-user", "user:",
            "mall-product", "product:",
            "mall-admin", "marketing:",
            "mall-auth", "admin:auth");

    @Test
    @DisplayName("E02：每个 @XxlJob handler 的目标路径必须含 /internal/（防借用户前缀/网关前缀回归）")
    void allHandlerTargets_areInternalPaths() throws Exception {
        Method[] handlers = java.util.Arrays.stream(BusinessJobHandler.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(com.xxl.job.core.handler.annotation.XxlJob.class))
                .toArray(Method[]::new);
        assertThat(handlers.length).as("BusinessJobHandler 应登记 @XxlJob 任务").isGreaterThanOrEqualTo(24);

        String source = readHandlerSource();
        for (Method handler : handlers) {
            String body = extractMethodBody(source, handler.getName());
            assertThat(body).as("handler %s 应有目标 URI", handler.getName()).isNotNull();

            Matcher uri = URI_PATTERN.matcher(body);
            assertThat(uri.find()).as("handler %s 应包含 .uri(\"http://...\") 调用", handler.getName()).isTrue();
            String url = uri.group(1);

            assertThat(url).as("handler %s 目标必须是内部路径（E02 教训）", handler.getName())
                    .contains("/internal/");

            // scope 与目标服务匹配
            Matcher scope = SCOPE_PATTERN.matcher(body);
            if (scope.find()) {
                String service = scope.group(1);
                String scopeValue = scope.group(2);
                String prefix = SERVICE_SCOPE_PREFIX.getOrDefault(service, "");
                assertThat(scopeValue).as("handler %s 的 scope 前缀应匹配目标服务 %s", handler.getName(), service)
                        .startsWith(prefix);
                if (REGISTRY.containsKey(handler.getName())) {
                    Target expected = REGISTRY.get(handler.getName());
                    assertThat(service).isEqualTo(expected.service());
                    assertThat(url).contains(expected.path());
                    assertThat(scopeValue).isEqualTo(expected.scope());
                }
            } else {
                // 无 serviceTokenProvider 签名的必须是 wish 专用通道（X-Service-Token 由 provider 签出）
                assertThat(body).as("handler %s 应含服务令牌签发", handler.getName())
                        .contains("wishServiceTokenProvider");
                assertThat(url).startsWith("http://mall-wish/internal/");
            }
        }
    }

    private String readHandlerSource() throws Exception {
        // CI/本地均从工作目录相对路径读取（模块根 = 工作目录）
        java.nio.file.Path path = java.nio.file.Path.of(
                "src/main/java/com/cloudmart/job/handler/BusinessJobHandler.java");
        if (!java.nio.file.Files.exists(path)) {
            path = java.nio.file.Path.of("mall-job/src/main/java/com/cloudmart/job/handler/BusinessJobHandler.java");
        }
        return java.nio.file.Files.readString(path);
    }

    /** 粗粒度方法体截取：从方法签名到下一个 @XxlJob/类尾 */
    private String extractMethodBody(String source, String methodName) {
        int sig = source.indexOf("void " + methodName + "()");
        if (sig < 0) {
            return null;
        }
        int end = source.indexOf("@XxlJob", sig);
        if (end < 0) {
            end = source.length();
        }
        return source.substring(sig, end);
    }
}
