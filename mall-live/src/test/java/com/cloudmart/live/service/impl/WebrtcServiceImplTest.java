package com.cloudmart.live.service.impl;

import com.cloudmart.live.dto.WebrtcSignalRequest;
import com.cloudmart.live.dto.WebrtcSignalResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebrtcServiceImpl 单元测试")
class WebrtcServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @InjectMocks
    private WebrtcServiceImpl service;

    @SuppressWarnings("unchecked")
    private void stubRedisOpsForList() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Nested
    @DisplayName("publishSignal 方法")
    class PublishSignalTest {

        @Test
        @DisplayName("发布信令 - 成功写入Redis列表")
        void shouldPublishSignalToRedis() {
            stubRedisOpsForList();
            WebrtcSignalRequest request = new WebrtcSignalRequest(1L, "OFFER", "sdp-offer-payload", "HOST");

            service.publishSignal(request);

            verify(listOperations).rightPush(eq("live:webrtc:signal:1:HOST"), eq("OFFER|sdp-offer-payload"));
            verify(redisTemplate).expire(eq("live:webrtc:signal:1:HOST"), anyLong(), any());
        }

        @Test
        @DisplayName("发布 ANSWER 信令 - VIEWER 角色")
        void shouldPublishAnswerSignal() {
            stubRedisOpsForList();
            WebrtcSignalRequest request = new WebrtcSignalRequest(2L, "ANSWER", "sdp-answer-payload", "VIEWER");

            service.publishSignal(request);

            verify(listOperations).rightPush(eq("live:webrtc:signal:2:VIEWER"), eq("ANSWER|sdp-answer-payload"));
        }
    }

    @Nested
    @DisplayName("getSignals 方法")
    class GetSignalsTest {

        @Test
        @DisplayName("获取信令列表 - 有数据")
        void shouldGetSignalsWithData() {
            stubRedisOpsForList();
            when(listOperations.range("live:webrtc:signal:1:HOST", 0, -1))
                    .thenReturn(List.of("OFFER|sdp-offer", "ANSWER|sdp-answer"));

            List<WebrtcSignalResponse> result = service.getSignals(1L, "HOST");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).type()).isEqualTo("OFFER");
            assertThat(result.get(0).payload()).isEqualTo("sdp-offer");
            assertThat(result.get(0).role()).isEqualTo("HOST");
            assertThat(result.get(1).type()).isEqualTo("ANSWER");
        }

        @Test
        @DisplayName("获取信令列表 - 无数据")
        void shouldGetEmptySignals() {
            stubRedisOpsForList();
            when(listOperations.range(anyString(), anyLong(), anyLong())).thenReturn(null);

            List<WebrtcSignalResponse> result = service.getSignals(1L, "HOST");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("获取信令列表 - 过滤格式不正确的数据")
        void shouldFilterMalformedSignals() {
            stubRedisOpsForList();
            when(listOperations.range("live:webrtc:signal:1:HOST", 0, -1))
                    .thenReturn(List.of("OFFER|sdp-offer", "malformed-no-pipe"));

            List<WebrtcSignalResponse> result = service.getSignals(1L, "HOST");

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().type()).isEqualTo("OFFER");
        }
    }

    @Nested
    @DisplayName("publishIceCandidate 方法")
    class PublishIceCandidateTest {

        @Test
        @DisplayName("发布 ICE 候选者 - 成功写入Redis")
        void shouldPublishIceCandidate() {
            stubRedisOpsForList();
            WebrtcSignalRequest request = new WebrtcSignalRequest(1L, "ICE_CANDIDATE", "ice-candidate-json", "HOST");

            service.publishIceCandidate(request);

            verify(listOperations).rightPush(eq("live:webrtc:ice:1:HOST"), eq("ice-candidate-json"));
            verify(redisTemplate).expire(eq("live:webrtc:ice:1:HOST"), anyLong(), any());
        }
    }

    @Nested
    @DisplayName("getIceCandidates 方法")
    class GetIceCandidatesTest {

        @Test
        @DisplayName("获取 ICE 候选者 - 有数据")
        void shouldGetIceCandidates() {
            stubRedisOpsForList();
            when(listOperations.range("live:webrtc:ice:1:HOST", 0, -1))
                    .thenReturn(List.of("candidate1", "candidate2"));

            List<String> result = service.getIceCandidates(1L, "HOST");

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly("candidate1", "candidate2");
        }

        @Test
        @DisplayName("获取 ICE 候选者 - 无数据返回空列表")
        void shouldReturnEmptyWhenNoIceCandidates() {
            stubRedisOpsForList();
            when(listOperations.range(anyString(), anyLong(), anyLong())).thenReturn(null);

            List<String> result = service.getIceCandidates(1L, "HOST");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("clearSignals 方法")
    class ClearSignalsTest {

        @Test
        @DisplayName("清除房间所有信令 - 删除4个Redis Key")
        void shouldClearAllSignalsForRoom() {
            service.clearSignals(1L);

            verify(redisTemplate).delete("live:webrtc:signal:1:HOST");
            verify(redisTemplate).delete("live:webrtc:signal:1:VIEWER");
            verify(redisTemplate).delete("live:webrtc:ice:1:HOST");
            verify(redisTemplate).delete("live:webrtc:ice:1:VIEWER");
        }
    }
}
