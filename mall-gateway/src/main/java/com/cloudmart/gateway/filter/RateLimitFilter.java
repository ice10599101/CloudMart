package com.cloudmart.gateway.filter;

import com.cloudmart.common.constant.SecurityConstants;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String AUTH_PATH_PREFIX = "/api/auth/";
    private static final String ADMIN_AUTH_PATH_PREFIX = "/api/admin/auth/";
    private static final String SECKILL_PATH_PREFIX = "/api/seckill/";

    private static final int AUTH_CAPACITY = 10;
    private static final Duration AUTH_REFILL_PERIOD = Duration.ofMinutes(1);

    private static final int SECKILL_CAPACITY = 20;
    private static final Duration SECKILL_REFILL_PERIOD = Duration.ofSeconds(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith(AUTH_PATH_PREFIX) || path.startsWith(ADMIN_AUTH_PATH_PREFIX)) {
            String clientIp = resolveClientIp(exchange);
            Bucket bucket = buckets.computeIfAbsent("auth:" + clientIp, key -> createAuthBucket());
            if (!bucket.tryConsume(1)) {
                log.warn("Auth rate limit exceeded for IP: {}", clientIp);
                return writeRateLimitResponse(exchange, "RATE_LIMIT_EXCEEDED",
                        "认证接口请求过于频繁，请稍后再试");
            }
        }

        if (path.startsWith(SECKILL_PATH_PREFIX) && !path.startsWith("/api/seckill/admin/")) {
            String userId = resolveUserId(exchange);
            Bucket bucket = buckets.computeIfAbsent("seckill:" + userId, key -> createSeckillBucket());
            if (!bucket.tryConsume(1)) {
                log.warn("Seckill rate limit exceeded for user: {}", userId);
                return writeRateLimitResponse(exchange, "RATE_LIMIT_EXCEEDED",
                        "秒杀接口请求过于频繁，请稍后再试");
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private Bucket createAuthBucket() {
        Bandwidth bandwidth = Bandwidth.classic(AUTH_CAPACITY,
                Refill.intervally(AUTH_CAPACITY, AUTH_REFILL_PERIOD));
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    private Bucket createSeckillBucket() {
        Bandwidth bandwidth = Bandwidth.classic(SECKILL_CAPACITY,
                Refill.intervally(SECKILL_CAPACITY, SECKILL_REFILL_PERIOD));
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    private String resolveUserId(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        return resolveClientIp(exchange);
    }

    private Mono<Void> writeRateLimitResponse(ServerWebExchange exchange, String code, String message) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String json = "{\"success\":false,\"data\":{},\"error\":{\"code\":\""
                + code + "\",\"message\":\"" + message + "\",\"details\":[]},\"meta\":{}}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
