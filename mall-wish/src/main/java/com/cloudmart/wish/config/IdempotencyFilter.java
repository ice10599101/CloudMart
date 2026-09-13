package com.cloudmart.wish.config;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

/**
 * X-Idempotency-Key 幂等冲突校验（规格 39 章幂等契约：同 key 异 request_hash → 409；
 * 同 key 同 hash → 重放首次成功响应）。
 *
 * <p>策略：仅拦截写方法（POST/PUT/DELETE）且携带幂等键的请求。哈希取请求体 SHA-256。
 * Redis 失效时 Fail-Open 直接放行——最坏退化为无幂等保护（与既有行为一致），不阻塞业务（39.6）。
 * 处理中窗口 60s，成功响应缓存 24h；失败（非 2xx）不缓存，允许客户端换体或原体重试。</p>
 *
 * <p>实现注意（BUG#43）：请求体哈希需要预读 body，而
 * {@code ContentCachingRequestWrapper} 预读后下游 {@code @RequestBody} 拿到的是
 * 已耗尽的空流（不可重复读），导致所有带幂等键的 JSON 写接口报
 * INVALID_REQUEST_BODY。这里改用可重复读包装器——body 一次性读入内存，
 * {@code getInputStream()/getReader()} 每次返回全新流，下游可正常解析。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "wish:idem:";
    private static final String PROCESSING_MARK = "processing|";
    private static final Duration PROCESSING_TTL = Duration.ofSeconds(60);
    private static final Duration SUCCESS_TTL = Duration.ofHours(24);
    private static final int MAX_CACHED_BODY = 256 * 1024;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        boolean writeMethod = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
        return !writeMethod || request.getHeader("X-Idempotency-Key") == null
                || request.getHeader(SecurityConstants.USER_ID_HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader(SecurityConstants.USER_ID_HEADER);
        String idemKey = request.getHeader("X-Idempotency-Key").trim();
        if (idemKey.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        String redisKey = KEY_PREFIX + userId + ":" + idemKey;

        // 预读请求体（可重复读包装器：下游 @RequestBody 从内存流重新解析）
        ReReadableRequestWrapper wrappedRequest = new ReReadableRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        if (wrappedRequest.cachedBody().length > MAX_CACHED_BODY) {
            // 超上限放弃幂等保护直接放行（body 已在包装器内存中，下游解析不受影响）
            log.warn("幂等键请求体超上限({}B)，放弃幂等保护直接放行", wrappedRequest.cachedBody().length);
            chain.doFilter(wrappedRequest, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
            return;
        }
        String bodyHash = sha256(wrappedRequest.cachedBody());

        String stored;
        try {
            stored = redisTemplate.opsForValue().get(redisKey);
        } catch (Exception ex) {
            log.warn("幂等键 Redis 读取失败，Fail-Open 放行: {}", ex.getMessage());
            chain.doFilter(wrappedRequest, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
            return;
        }

        if (stored != null) {
            int sep = stored.indexOf('|');
            String storedHash = sep >= 0 ? stored.substring(0, sep) : "";
            String payload = sep >= 0 ? stored.substring(sep + 1) : "";
            if (!storedHash.equals(bodyHash)) {
                reject(response, "IDEMPOTENCY_KEY_REUSED",
                        "幂等键已被不同的请求内容使用，请更换 X-Idempotency-Key 后重试");
                return;
            }
            if (!payload.isEmpty()) {
                replay(response, payload);
                return;
            }
            reject(response, "IDEMPOTENCY_KEY_REUSED",
                    "相同幂等键的请求正在处理中，请稍后查询结果");
            return;
        }

        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(redisKey,
                    PROCESSING_MARK + bodyHash, PROCESSING_TTL);
        } catch (Exception ex) {
            log.warn("幂等键 Redis 占位失败，Fail-Open 放行: {}", ex.getMessage());
            chain.doFilter(wrappedRequest, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
            return;
        }
        if (Boolean.FALSE.equals(acquired)) {
            // 与上方 GET 之间几乎不可能进入（占位成功者 60s 内完成），按处理中拒绝
            reject(response, "IDEMPOTENCY_KEY_REUSED", "相同幂等键的请求正在处理中，请稍后查询结果");
            return;
        }

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
            int status = wrappedResponse.getStatus();
            if (status >= 200 && status < 300) {
                byte[] body = wrappedResponse.getContentAsByteArray();
                if (body.length <= MAX_CACHED_BODY) {
                    redisTemplate.opsForValue().set(redisKey,
                            bodyHash + "|" + status + "|" + new String(body, StandardCharsets.UTF_8),
                            SUCCESS_TTL);
                }
            } else {
                // 失败不缓存，允许原键重试
                redisTemplate.delete(redisKey);
            }
        } catch (Exception ex) {
            redisTemplate.delete(redisKey);
            throw ex;
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }

    /**
     * 可重复读请求包装器：body 预读进内存，getInputStream/getReader 每次返回全新流。
     */
    private static class ReReadableRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        ReReadableRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        byte[] cachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffer = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return buffer.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    private void reject(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(409);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(code, message)));
    }

    private void replay(HttpServletResponse response, String payload) throws IOException {
        int sep = payload.indexOf('|');
        int status = sep >= 0 ? Integer.parseInt(payload.substring(0, sep)) : 200;
        String body = sep >= 0 ? payload.substring(sep + 1) : payload;
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    private String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
