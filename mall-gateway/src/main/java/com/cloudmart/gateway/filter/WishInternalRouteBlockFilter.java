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

/**
 * B01 安全止血：在路由与身份注入之前拒绝所有外部直达 mall-wish 管理端与内部端点的请求。
 *
 * <p>漏洞背景：网关 {@code /api/wish/**} 为通配路由，{@code AdminAuthGlobalFilter} 仅校验
 * {@code /api/admin/} 前缀；携带有效用户 JWT 的请求可直达 {@code /api/wish/admin/**}
 * （后台管理）与 {@code /api/wish/internal/**}（任务调度、宠物钱包加减星光）。</p>
 *
 * <p>管理端唯一合法链路是 mall-admin 经 Nacos 内部 Feign 直连（不经网关），
 * 任务链路是 mall-job 内部直连；因此对外部流量直接 404，不存在需要放行的例外。</p>
 *
 * <p>规范化策略：先做百分号解码（非法转义直接拒绝），再逐段剥离 {@code ;matrix} 参数、
 * 解析 {@code .}/ {@code ..} 点段、折叠重复斜杠、去除尾部斜杠、统一小写后匹配。
 * 任何包含点段或解码失败的路径一律拒绝——本服务的合法 API 不含这些形态。</p>
 */
@Slf4j
@Component
public class WishInternalRouteBlockFilter implements GlobalFilter, Ordered {

    static final String BLOCKED_PREFIX_ADMIN = "/api/wish/admin";
    static final String BLOCKED_PREFIX_INTERNAL = "/api/wish/internal";

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
        return matchesPrefix(normalizedPath, BLOCKED_PREFIX_ADMIN)
                || matchesPrefix(normalizedPath, BLOCKED_PREFIX_INTERNAL);
    }

    private static boolean matchesPrefix(String normalizedPath, String prefix) {
        return normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/");
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
        log.warn("[B01 BLOCK] 拒绝外部访问 wish 管理端/内部端点: method={} path={} from={}",
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
