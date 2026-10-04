package com.cloudmart.wish.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T17 幂等键语义：键 = 身份 + intentId（hash 仅存值中）——
 * ① 同键同参重放返回原响应；② 同键异参 409 IDEMPOTENCY_KEY_REUSED
 * （旧实现 hash 入键，同键异参落不同键根本不相遇）；③ 并发同键 409 IN_PROGRESS；
 * ④ 失败不缓存，原键可重试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyFilter 单元测试（T17）")
class IdempotencyFilterTest {

    private static final String USER_ID = "1001";
    private static final String IDEM_KEY = "intent-abc-1";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private FilterChain chain;

    private IdempotencyFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 模拟 Redis：键 → 值 */
    private final java.util.Map<String, String> store = new java.util.HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenAnswer(inv ->
                store.get(inv.getArgument(0)));
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> store.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        lenient().doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(redisTemplate.delete(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0)) != null);
        filter = new IdempotencyFilter(redisTemplate, objectMapper);
    }

    private MockHttpServletRequest request(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/wish/wishes/2001/checkin");
        request.setRequestURI("/wish/wishes/2001/checkin");
        request.addHeader("X-Idempotency-Key", IDEM_KEY);
        request.addHeader("X-User-Id", USER_ID);
        request.setContentType("application/json");
        request.setContent(body == null ? new byte[0] : body.getBytes());
        return request;
    }

    private MockHttpServletResponse runFilter(MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, (req, res) -> {
                // ContentCachingResponseWrapper extends HttpServletResponseWrapper——转型安全
                jakarta.servlet.http.HttpServletResponse httpRes =
                        (jakarta.servlet.http.HttpServletResponse) res;
                httpRes.setStatus(200);
                httpRes.setContentType("application/json");
                httpRes.setCharacterEncoding("UTF-8");
                httpRes.getWriter().write("{\"success\":true,\"data\":{\"id\":77}}");
            });
        } catch (Exception ex) {
            throw ex;
        }
        return response;
    }

    @Test
    @DisplayName("键不含请求 hash：Redis 键 = wish:idem:{userId}:{intentId}")
    void redisKey_excludesHash() throws Exception {
        runFilter(request("{\"content\":\"day 1\"}"));

        verify(valueOperations).setIfAbsent(
                eq("wish:idem:" + USER_ID + ":" + IDEM_KEY), contains("\"h\""), any(Duration.class));
    }

    @Test
    @DisplayName("T17 回归：同键异参 → 409 IDEMPOTENCY_KEY_REUSED（旧实现落不同键不相遇）")
    void sameKeyDifferentBody_rejected() throws Exception {
        MockHttpServletResponse first = runFilter(request("{\"content\":\"day 1\"}"));
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(first.getContentAsString()).contains("\"id\":77");

        // 同键换内容重放：必须被拒绝，而不是静默执行第二笔业务
        MockHttpServletResponse second = runFilter(request("{\"content\":\"day 2\"}"));
        assertThat(second.getStatus()).isEqualTo(409);
        assertThat(second.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("同键同参：重放返回原响应（不重复执行业务）")
    void sameKeySameBody_replays() throws Exception {
        MockHttpServletResponse first = runFilter(request("{\"content\":\"day 1\"}"));

        MockHttpServletResponse second = runFilter(request("{\"content\":\"day 1\"}"));

        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
    }

    @Test
    @DisplayName("处理中：并发同键 409 WISH_OPERATION_IN_PROGRESS")
    void concurrentSameKey_processingRejected() throws Exception {
        // 先完成一次请求取得真实 hash，再把值改写为处理中标记（同 hash 无 body）
        runFilter(request("{\"content\":\"day 1\"}"));
        String stored = store.get("wish:idem:" + USER_ID + ":" + IDEM_KEY);
        String realHash = objectMapper.readTree(stored).path("h").asText();
        store.put("wish:idem:" + USER_ID + ":" + IDEM_KEY, "{\"h\":\"" + realHash + "\"}");

        MockHttpServletResponse response = runFilter(request("{\"content\":\"day 1\"}"));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("WISH_OPERATION_IN_PROGRESS");
    }

    @Test
    @DisplayName("失败不缓存：下游异常后 Redis 占位被清除，原键可重试")
    void failure_notCached_retryable() throws Exception {
        MockHttpServletResponse failing = new MockHttpServletResponse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                filter.doFilter(request("{\"content\":\"day 1\"}"), failing, (req, res) -> {
                    throw new ServletException("business failure");
                })).isInstanceOf(ServletException.class);

        assertThat(store).doesNotContainKey("wish:idem:" + USER_ID + ":" + IDEM_KEY);
    }
}
