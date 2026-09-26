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
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * X-Idempotency-Key Redis 加速层（B04 重写）。
 *
 * <p>定位：只做快速重放与瞬时并发去重的<b>加速层</b>，不是业务凭证——
 * 扣费/兑换/送礼等资源操作的最终幂等由 {@code wish_operation}（与领域写同事务的
 * 持久操作记录）保证，Redis 清空或本层故障不产生重复扣费。</p>
 *
 * <p>相对旧实现的修复（任务书 B04）：</p>
 * <ul>
 *   <li>作用域键包含 method+path+规范化 query——不同接口/不同 query 不再串用结果；</li>
 *   <li>摘要覆盖 method+path+query+body（旧实现仅 body）；</li>
 *   <li>处理中状态与结果统一 JSON 存储——修复旧"processing|hash"被误判为
 *       异请求并把哈希当响应体重放的解析缺陷；</li>
 *   <li>处理中返回 409 {@code WISH_OPERATION_IN_PROGRESS}（语义区分于键复用）；</li>
 *   <li>过滤器排序在 Spring Security 链之后（先认证、再查幂等）；</li>
 *   <li>仅 2xx 缓存响应；认证/参数错误不缓存，允许原键重试。</li>
 * </ul>
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "wish:idem:";
    private static final Duration PROCESSING_TTL = Duration.ofSeconds(60);
    private static final Duration SUCCESS_TTL = Duration.ofHours(24);
    private static final int MAX_CACHED_BODY = 256 * 1024;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        boolean writeMethod = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)
                || "DELETE".equals(method);
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

        // 预读请求体（可重复读包装器：下游 @RequestBody 从内存流重新解析）
        ReReadableRequestWrapper wrappedRequest = new ReReadableRequestWrapper(request);
        if (wrappedRequest.cachedBody().length > MAX_CACHED_BODY) {
            // 超上限放弃加速层（持久层仍兜底资源操作），直接放行
            log.warn("幂等键请求体超上限({}B)，跳过 Redis 加速层", wrappedRequest.cachedBody().length);
            chain.doFilter(wrappedRequest, response);
            return;
        }
        String method = request.getMethod();
        String path = request.getRequestURI();
        String canonicalQuery = canonicalQuery(request.getParameterMap());
        String requestHash = sha256((method + "\n" + path + "\n" + canonicalQuery + "\n")
                .getBytes(StandardCharsets.UTF_8), wrappedRequest.cachedBody());
        String redisKey = KEY_PREFIX + userId + ":" + idemKey + ":" + requestHash;

        try {
            String stored = redisTemplate.opsForValue().get(redisKey);
            if (stored != null) {
                replayOrReject(response, stored, requestHash);
                return;
            }
        } catch (Exception ex) {
            // Redis 故障：跳过加速层放行（fail-open），持久幂等层兜底
            log.warn("幂等加速层 Redis 读取失败，放行: {}", ex.getMessage());
            chain.doFilter(wrappedRequest, response);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey,
                    processingMark(requestHash), PROCESSING_TTL);
            if (!Boolean.TRUE.equals(acquired)) {
                reject(response, "WISH_OPERATION_IN_PROGRESS",
                        "相同幂等键的请求正在处理中，请稍后按原键查询结果");
                return;
            }
        } catch (Exception ex) {
            log.warn("幂等加速层 Redis 占位失败，放行: {}", ex.getMessage());
            chain.doFilter(wrappedRequest, response);
            return;
        }

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
            int status = wrappedResponse.getStatus();
            if (status >= 200 && status < 300) {
                byte[] body = wrappedResponse.getContentAsByteArray();
                if (body.length <= MAX_CACHED_BODY) {
                    redisTemplate.opsForValue().set(redisKey, completedMark(requestHash, status,
                            new String(body, StandardCharsets.UTF_8)), SUCCESS_TTL);
                } else {
                    redisTemplate.delete(redisKey);
                }
            } else {
                // 失败不缓存：允许原键重试
                redisTemplate.delete(redisKey);
            }
        } catch (Exception ex) {
            redisTemplate.delete(redisKey);
            throw ex;
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }

    /** 统一 JSON 存储格式：{"h":hash} 处理中；{"h":hash,"s":status,"b":body} 已完成。 */
    private String processingMark(String hash) {
        return toJson(Map.of("h", hash));
    }

    private String completedMark(String hash, int status, String body) {
        return toJson(Map.of("h", hash, "s", status, "b", body));
    }

    private void replayOrReject(HttpServletResponse response, String stored, String requestHash)
            throws IOException {
        StoredEntry entry = readEntry(stored);
        if (entry == null || !requestHash.equals(entry.hash())) {
            reject(response, "IDEMPOTENCY_KEY_REUSED",
                    "幂等键已被不同的请求内容使用，请更换 X-Idempotency-Key 后重试");
            return;
        }
        if (entry.body() == null) {
            reject(response, "WISH_OPERATION_IN_PROGRESS",
                    "相同幂等键的请求正在处理中，请稍后按原键查询结果");
            return;
        }
        response.setStatus(entry.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(entry.body());
    }

    private StoredEntry readEntry(String json) {
        try {
            var node = objectMapper.readTree(json);
            String hash = node.path("h").asText(null);
            if (hash == null) {
                return null;
            }
            String body = node.has("b") && !node.get("b").isNull() ? node.get("b").asText() : null;
            int status = node.path("s").asInt(200);
            return new StoredEntry(hash, status, body);
        } catch (Exception ex) {
            return null;
        }
    }

    private record StoredEntry(String hash, int status, String body) {
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            // 序列化失败不应放行重复占位：退化为仅摘要标记
            return "{\"h\":\"" + "encode-error" + "\"}";
        }
    }

    /** query 规范化：参数名排序、同名参数按值排序，保证同语义请求摘要稳定。 */
    private String canonicalQuery(Map<String, String[]> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        Map<String, List<String>> sorted = new TreeMap<>();
        for (var entry : params.entrySet()) {
            List<String> values = new ArrayList<>(List.of(entry.getValue()));
            Collections.sort(values);
            sorted.put(entry.getKey(), values);
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : sorted.entrySet()) {
            for (String value : entry.getValue()) {
                if (!sb.isEmpty()) {
                    sb.append('&');
                }
                sb.append(entry.getKey()).append('=').append(value);
            }
        }
        return sb.toString();
    }

    private String sha256(byte[] prefix, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prefix);
            digest.update(body);
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private void reject(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(409);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(code, message)));
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
}
