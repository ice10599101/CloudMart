package com.cloudmart.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * B01/SEC-01 安全止血：在路由与身份注入之前拒绝所有外部直达各服务管理端与内部端点的请求。
 *
 * <p>漏洞背景：网关 {@code /api/{service}/**} 为通配路由（StripPrefix=2），
 * {@code AdminAuthGlobalFilter} 仅校验 {@code /api/admin/} 前缀；携带有效用户 JWT 的请求
 * 可直达 {@code /api/wish/admin/**}、{@code /api/pet/admin/**}（后台管理）与
 * {@code /api/wish/internal/**}、{@code /api/pet/internal/**}（任务调度、钱包加减等）。</p>
 *
 * <p>管理端唯一合法链路是 mall-admin 经 Nacos 内部 Feign 直连（不经网关），
 * 任务链路是 mall-job 内部直连；因此对外部流量直接 404，不存在需要放行的例外。
 * 新增服务的 admin/internal 端点必须同步加入 {@link #BLOCKED_PREFIXES}。</p>
 *
 * <p>规范化策略：先做百分号解码（非法转义直接拒绝），再逐段剥离 {@code ;matrix} 参数、
 * 解析 {@code .}/{@code ..} 点段、折叠重复斜杠、去除尾部斜杠、统一小写后匹配。
 * 任何包含点段或解码失败的路径一律拒绝——本网关的合法 API 不含这些形态。</p>
 */
@Slf4j
@Component
public class ServiceInternalRouteBlockFilter implements GlobalFilter, Ordered {

    /**
     * 各服务经网关不可直达的管理端/内部端点前缀（小写、带 /api 前缀）。
     * SEC-01：覆盖全部业务服务——管理端唯一合法链路是 mall-admin 经 Nacos 内部
     * Feign 直连（携带服务令牌），业务服务间调用同为 Nacos 直连，均不经网关。
     * 新增服务的 admin/internal 端点必须同步加入本清单。
     */
    static final Set<String> BLOCKED_PREFIXES = Set.of(
            "/api/user/admin", "/api/user/internal",
            "/api/product/admin", "/api/product/internal",
            "/api/order/admin", "/api/order/internal",
            "/api/payment/admin", "/api/payment/internal",
            "/api/inventory/admin", "/api/inventory/internal",
            "/api/cart/admin", "/api/cart/internal",
            "/api/coupon/admin", "/api/coupon/internal",
            "/api/seckill/admin", "/api/seckill/internal",
            "/api/marketing/admin", "/api/marketing/internal",
            "/api/wms/admin", "/api/wms/internal",
            "/api/community/admin", "/api/community/internal",
            "/api/notification/admin", "/api/notification/internal",
            "/api/live/admin", "/api/live/internal",
            "/api/ai/admin", "/api/ai/internal",
            "/api/risk/admin", "/api/risk/internal",
            "/api/file/admin", "/api/file/internal",
            "/api/job/admin", "/api/job/internal",
            "/api/gen/admin", "/api/gen/internal",
            "/api/wish/admin", "/api/wish/internal",
            "/api/pet/admin", "/api/pet/internal");

    private static final String BLOCK_RESPONSE_JSON =
            "{\"success\":false,\"data\":null,\"error\":{\"code\":\"NOT_FOUND\","
                    + "\"message\":\"资源不存在\",\"details\":[]},\"meta\":{}}";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        String normalized = normalize(rawPath);
        if (normalized == null) {
            return reject(exchange, rawPath);
        }
        if (isBlocked(normalized)) {
            return reject(exchange, rawPath);
        }
        return chain.filter(exchange);
    }

    static boolean isBlocked(String normalizedPath) {
        for (String prefix : BLOCKED_PREFIXES) {
            if (normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return 规范化后的路径；输入为非法路径（解码失败、包含点段）时返回 null
     */
    static String normalize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return rawPath;
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        for (String segment : decoded.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            int matrixIndex = segment.indexOf(';');
            if (matrixIndex >= 0) {
                segment = segment.substring(0, matrixIndex);
            }
            if (segment.isEmpty()) {
                continue;
            }
            // 点段在合法 API 中不存在：一律视为路径穿越尝试而非做相对解析
            if (".".equals(segment) || "..".equals(segment)) {
                return null;
            }
            segments.add(segment.toLowerCase());
        }
        return "/" + String.join("/", segments);
    }

    private Mono<Void> reject(ServerWebExchange exchange, String rawPath) {
        log.warn("[B01 BLOCK] 拒绝外部访问服务管理端/内部端点: method={} path={} from={}",
                exchange.getRequest().getMethod(), rawPath,
                exchange.getRequest().getRemoteAddress());
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = BLOCK_RESPONSE_JSON.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 必须先于 JwtAuthenticationFilter(+1000)/AdminAuthGlobalFilter(+1001)：
     * 被阻断路径不做任何身份注入，也不进入路由。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 500;
    }
}
